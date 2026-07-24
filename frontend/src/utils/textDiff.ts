export interface TextDiffSegment {
  type: 'same' | 'added' | 'removed'
  value: string
}

const splitText = (text: string): string[] => {
  if (!text) return []
  const paragraphs = text.split(/(\n{2,})/).filter(Boolean)
  if (paragraphs.length > 120) {
    return paragraphs
  }
  return text.match(/[^。！？!?；;\n]+[。！？!?；;]?|\n+/g) || [text]
}

export const diffText = (original: string, revised: string): TextDiffSegment[] => {
  const left = splitText(original)
  const right = splitText(revised)

  if (left.length * right.length > 360000) {
    return [
      { type: 'removed', value: original },
      { type: 'added', value: revised },
    ]
  }

  const matrix = Array.from({ length: left.length + 1 }, () =>
    new Uint16Array(right.length + 1),
  )

  for (let i = 1; i <= left.length; i += 1) {
    for (let j = 1; j <= right.length; j += 1) {
      matrix[i][j] =
        left[i - 1] === right[j - 1]
          ? matrix[i - 1][j - 1] + 1
          : Math.max(matrix[i - 1][j], matrix[i][j - 1])
    }
  }

  const result: TextDiffSegment[] = []
  let i = left.length
  let j = right.length

  while (i > 0 || j > 0) {
    if (i > 0 && j > 0 && left[i - 1] === right[j - 1]) {
      result.push({ type: 'same', value: left[i - 1] })
      i -= 1
      j -= 1
    } else if (j > 0 && (i === 0 || matrix[i][j - 1] >= matrix[i - 1][j])) {
      result.push({ type: 'added', value: right[j - 1] })
      j -= 1
    } else {
      result.push({ type: 'removed', value: left[i - 1] })
      i -= 1
    }
  }

  return result.reverse().reduce<TextDiffSegment[]>((segments, segment) => {
    const previous = segments[segments.length - 1]
    if (previous?.type === segment.type) {
      previous.value += segment.value
    } else {
      segments.push({ ...segment })
    }
    return segments
  }, [])
}
