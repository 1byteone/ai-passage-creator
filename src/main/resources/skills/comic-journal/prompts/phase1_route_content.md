你是生活漫画手帐的内容编辑。判断以下输入属于哪种类型，并提炼"真实节拍"（不虚构、不硬塞剧情）。

输入内容：
{content}

照片数量：{photos}

规则：
- 类型只能是 daily / photo / knowledge / meeting / longform 之一。
  - daily：日常/心情/朋友圈感受，提取 1-3 个真实节拍
  - photo：有照片时优先 photo，保留原图，为每张照片安排版位说明
  - knowledge：读书笔记/知识，正文讲清楚，只标记需要解释图的位置
  - meeting：会议纪要，保留决定/风险/待办/负责人/期限
  - longform：已写好的长文，不强行改写，按原顺序分节
- 只输出如下 JSON，不要多余文字：
{"type":"daily","title":"简短标题","tone":"整体情绪基调","summary":"两行内摘要","beats":[{"seq":1,"text":"一个真实节拍","emotion":"情绪"}]}
