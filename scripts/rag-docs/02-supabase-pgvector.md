## Vector columns | Supabase Docs

**Source**: https://supabase.com/docs/guides/ai/vector-columns

---

[Skip to content](/charset=utf-8#docs-content-container)

AI & Vectors

Supabase offers a number of different ways to store and query vectors within Postgres. The SQL included in this guide is applicable for clients in all programming languages. If you are a Python user, see your [Python client options](/docs/guides/ai/python-clients) after reading the `Learn` section.

Vectors in Supabase are enabled via [pgvector](https://github.com/pgvector/pgvector/), a Postgres extension for storing and querying vectors in Postgres. It can be used to store [embeddings](/docs/guides/ai/concepts#what-are-embeddings).

## Usage[#](/charset=utf-8#usage)

### Enable the extension[#](/charset=utf-8#enable-the-extension)

1. Go to the [Database](/dashboard/project/_/database/tables) page in the Dashboard.
2. Click on **Extensions** in the sidebar.
3. Search for "vector" and enable the extension.

### Create a table to store vectors[#](/charset=utf-8#create-a-table-to-store-vectors)

After enabling the `vector` extension, you will get access to a new data type called `vector`. The size of the vector (indicated in parentheses) represents the number of dimensions stored in that vector.

```
1create table documents (2  id serial primary key,3  title text not null,4  body text not null,5  embedding extensions.vector(384)6);
```

In the SQL snippet above, we create a `documents` table with an `embedding` column. This is a standard Postgres column, so you can name it anything you like. The `embedding` column uses the `vector` data type with 384 dimensions. Change this number to match the dimensions your embedding model produces. For example, if you're [generating embeddings](/docs/guides/ai/quickstarts/generate-text-embeddings) using the open source [gte-small](https://huggingface.co/Supabase/gte-small) model, set this to 384.

In general, embeddings with fewer dimensions perform best. See our [analysis on fewer dimensions in pgvector](/blog/fewer-dimensions-are-better-pgvector).

### Storing a vector / embedding[#](/charset=utf-8#storing-a-vector--embedding)

In this example we'll generate a vector using Transformers.js, then store it in the database using the Supabase JavaScript client.

```
1import { pipeline } from '@huggingface/transformers'23const generateEmbedding = await pipeline('feature-extraction', 'Supabase/gte-small')45const title = 'First post!'6const body = 'Hello world!'78// Generate a vector using Transformers.js9const output = await generateEmbedding(body, {10  pooling: 'mean',11  normalize: true,12})1314// Extract the embedding output15const embedding = Array.from(output.data)1617// Store the vector in Postgres18const { data, error } = await supabase.from('documents').insert({19  title,20  body,21  embedding,22})
```

This example uses the JavaScript Supabase client, but you can modify it to work with any [supported language library](/docs#client-libraries).

### Querying a vector / embedding[#](/charset=utf-8#querying-a-vector--embedding)

Similarity search is the most common use case for vectors. `pgvector` supports 3 new operators for computing distance:

| Operator | Description |
|---|---|
| <-> | Euclidean distance |
| <#> | negative inner product |
| <=> | cosine distance |

Choosing the right operator depends on your needs. Dot product tends to be the fastest if your vectors are normalized. For more information on how embeddings work and how they relate to each other, see [What are Embeddings?](/docs/guides/ai/concepts#what-are-embeddings).

Supabase client libraries like `supabase-js` connect to your Postgres instance via [PostgREST](/docs/guides/getting-started/architecture#postgrest-api). PostgREST does not currently support `pgvector` similarity operators, so we'll need to wrap our query in a Postgres function and call it via the `rpc()` method:

```
1create or replace function match_documents (2  query_embedding extensions.vector(384),3  match_threshold float,4  match_count int5)6returns table (7  id bigint,8  title text,9  body text,10  similarity float11)12language sql stable13as $$14  select15    documents.id,16    documents.title,17    documents.body,18    1 - (documents.embedding <=> query_embedding) as similarity19  from documents20  where 1 - (documents.embedding <=> query_embedding) > match_threshold21  order by (documents.embedding <=> query_embedding) asc22  limit match_count;23$$;
```

This function takes a `query_embedding` argument and compares it to all other embeddings in the `documents` table. Each comparison returns a similarity score. If the similarity is greater than the `match_threshold` argument, it is returned. The number of rows returned is limited by the `match_count` argument.

Feel free to modify this method to fit the needs of your application. The `match_threshold` ensures that only documents that have a minimum similarity to the `query_embedding` are returned. Without this, you may end up returning documents that subjectively don't match. This value will vary for each application - you will need to perform your own testing to determine the threshold that makes sense for your app.

If you index your vector column, ensure that the `order by` sorts by the distance function directly (rather than sorting by the calculated `similarity` column, which may lead to the index being ignored and poor performance).

To execute the function from your client library, call `rpc()` with the name of your Postgres function:

```
1const { data: documents } = await supabaseClient.rpc('match_documents', {2  query_embedding: embedding, // Pass the embedding you want to compare3  match_threshold: 0.78, // Choose an appropriate threshold for your data4  match_count: 10, // Choose the number of matches5})
```

In this example `embedding` would be another embedding you wish to compare against your table of pre-generated embedding documents. For example if you were building a search engine, every time the user submits their query you would first generate an embedding on the search query itself, then pass it into the above `rpc()` function to match.

To filter your vector search by another column from the JS client, extend the function above with an extra parameter and `where` clause. See [Filtering vector search by metadata](/docs/guides/ai/semantic-search#filtering-vector-search-by-metadata) for a worked example.

Be sure to use embeddings produced from the same embedding model when calculating distance. Comparing embeddings from two different models will produce no meaningful result.

Vectors and embeddings can be used for much more than search. Learn more about embeddings at [What are Embeddings?](/docs/guides/ai/concepts#what-are-embeddings).

### Indexes[#](/charset=utf-8#indexes)

Once your vector table starts to grow, you will likely want to add an index to speed up queries. See [Vector indexes](/docs/guides/ai/vector-indexes) to learn how vector indexes work and how to create them.

### Is this helpful?

### AI Tools

[Connect your AI agent](/docs/guides/ai-tools)[Ask ChatGPT](https://chatgpt.com/?hint=search&q=Read from https://supabase.com/docs/guides/ai/vector-columns so I can ask questions about its contents)[Ask Claude](https://claude.ai/new?q=Read from https://supabase.com/docs/guides/ai/vector-columns so I can ask questions about its contents)
