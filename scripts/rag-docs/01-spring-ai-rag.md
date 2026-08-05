## 检索增强生成（RAG） (Retrieval Augmented Generation (RAG)) | Spring AI1.0.4中文文档|Spring官方文档|SpringBoot 教程|Spring中文网

**Source**: https://spring-ai.spring-doc.cn/spring-ai/1.0.4/api_retrieval-augmented-generation.html

---

|  | 获取最新的快照版本，请使用 Spring AI 1.1.3！spring-doc.cadn.net.cn |
|---|---|

# 检索增强生成

检索增强生成（RAG）是一种有助于克服大型语言模型局限性的技术，这些局限性包括难以处理长篇内容、事实准确性以及上下文感知能力。[spring-doc.cadn.net.cn](https://spring-doc.cadn.net.cn/)

Spring AI 通过提供模块化架构来支持 RAG，使您可以自行构建自定义的 RAG 流程， 或使用 `Advisor` API 来直接使用开箱即用的 RAG 流程。[spring-doc.cadn.net.cn](https://spring-doc.cadn.net.cn/)

|  | 在concepts部分了解更多关于检索增强生成的信息。 |
|---|---|

## [/spring-ai/1.0.4/api_retrieval-augmented-generation.html#_advisors](/spring-ai/1.0.4/api_retrieval-augmented-generation.html#_advisors)通知器

Spring AI 提供开箱即用的支持，使用 `Advisor` API 实现常见的 RAG 流程。[spring-doc.cadn.net.cn](https://spring-doc.cadn.net.cn/)

要使用 `QuestionAnswerAdvisor` 或 `VectorStoreChatMemoryAdvisor`,您需要将 `spring-ai-advisors-vector-store` 依赖添加到您的项目中：[spring-doc.cadn.net.cn](https://spring-doc.cadn.net.cn/)

```
<dependency>
   <groupId>org.springframework.ai</groupId>
   <artifactId>spring-ai-advisors-vector-store</artifactId>
</dependency>
```

### [/spring-ai/1.0.4/api_retrieval-augmented-generation.html#_questionansweradvisor](/spring-ai/1.0.4/api_retrieval-augmented-generation.html#_questionansweradvisor)问题回答顾问

向量数据库存储了AI模型所不了解的数据。当用户问题被发送到AI模型时，`QuestionAnswerAdvisor`会查询向量数据库以获取与用户问题相关的文档。[spring-doc.cadn.net.cn](https://spring-doc.cadn.net.cn/)

向量数据库的响应被附加到用户文本中，以为 AI 模型生成响应提供上下文。[spring-doc.cadn.net.cn](https://spring-doc.cadn.net.cn/)

假设您已经将数据加载到`VectorStore`中，您可以通过向`ChatClient`提供`QuestionAnswerAdvisor`的实例来执行检索增强生成（RAG）。[spring-doc.cadn.net.cn](https://spring-doc.cadn.net.cn/)

```
ChatResponse response = ChatClient.builder(chatModel)
        .build().prompt()
        .advisors(new QuestionAnswerAdvisor(vectorStore))
        .user(userText)
        .call()
        .chatResponse();
```

在本示例中，`QuestionAnswerAdvisor` 将在向量数据库中的所有文档上执行相似性搜索。要限制搜索的文档类型，`SearchRequest` 会采用一种类似 SQL 的筛选表达式，该表达式可在所有 `VectorStores` 中通用。[spring-doc.cadn.net.cn](https://spring-doc.cadn.net.cn/)

此过滤器表达式可以在创建 `QuestionAnswerAdvisor` 时进行配置，因此将始终应用于所有 `ChatClient` 请求；或者也可以在运行时按每个请求单独提供。[spring-doc.cadn.net.cn](https://spring-doc.cadn.net.cn/)

以下是如何创建一个阈值为 `0.8` 的 `QuestionAnswerAdvisor` 实例，并返回前 `6` 个结果。[spring-doc.cadn.net.cn](https://spring-doc.cadn.net.cn/)

```
var qaAdvisor = QuestionAnswerAdvisor.builder(vectorStore)
        .searchRequest(SearchRequest.builder().similarityThreshold(0.8d).topK(6).build())
        .build();
```

#### [/spring-ai/1.0.4/api_retrieval-augmented-generation.html#_dynamic_filter_expressions](/spring-ai/1.0.4/api_retrieval-augmented-generation.html#_dynamic_filter_expressions)动态过滤表达式

在运行时使用`FILTER_EXPRESSION`顾问上下文参数更新`SearchRequest`筛选器表达式：[spring-doc.cadn.net.cn](https://spring-doc.cadn.net.cn/)

```
ChatClient chatClient = ChatClient.builder(chatModel)
    .defaultAdvisors(QuestionAnswerAdvisor.builder(vectorStore)
        .searchRequest(SearchRequest.builder().build())
        .build())
    .build();

// Update filter expression at runtime
String content = this.chatClient.prompt()
    .user("Please answer my question XYZ")
    .advisors(a -> a.param(QuestionAnswerAdvisor.FILTER_EXPRESSION, "type == 'Spring'"))
    .call()
    .content();
```

The `FILTER_EXPRESSION` parameter allows you to dynamically filter the search results based on the provided expression.[spring-doc.cadn.net.cn](https://spring-doc.cadn.net.cn/)

#### [/spring-ai/1.0.4/api_retrieval-augmented-generation.html#_custom_template](/spring-ai/1.0.4/api_retrieval-augmented-generation.html#_custom_template)自定义模板

The `QuestionAnswerAdvisor` uses a default template to augment the user question with the retrieved documents. You can customize this behavior by providing your own `PromptTemplate` object via the `.promptTemplate()` builder method.[spring-doc.cadn.net.cn](https://spring-doc.cadn.net.cn/)

|  | 此处提供的PromptTemplate用于自定义顾问如何将检索到的上下文与用户查询进行合并。这与在ChatClient本身上配置TemplateRenderer（使用.templateRenderer())不同，后者会影响顾问运行之前初始用户/系统提示内容的呈现。有关客户端级别模板渲染的更多详细信息，请参阅ChatClient 提示模板。 |
|---|---|

自定义的`PromptTemplate`可以使用任何`TemplateRenderer`实现（默认情况下，它使用基于[StringTemplate](https://www.stringtemplate.org/)引擎的`StPromptTemplate`）。关键要求是模板必须包含以下两个占位符：[spring-doc.cadn.net.cn](https://spring-doc.cadn.net.cn/)

- 

a `query` placeholder to receive the user question.[spring-doc.cadn.net.cn](https://spring-doc.cadn.net.cn/)

- 

a `question_answer_context` placeholder to receive the retrieved context.[spring-doc.cadn.net.cn](https://spring-doc.cadn.net.cn/)

```
PromptTemplate customPromptTemplate = PromptTemplate.builder()
    .renderer(StTemplateRenderer.builder().startDelimiterToken('<').endDelimiterToken('>').build())
    .template("""
            <query>

            Context information is below.

			---------------------
			<question_answer_context>
			---------------------

			Given the context information and no prior knowledge, answer the query.

			Follow these rules:

			1. If the answer is not in the context, just say that you don't know.
			2. Avoid statements like "Based on the context..." or "The provided information...".
            """)
    .build();

    String question = "Where does the adventure of Anacletus and Birba take place?";

    QuestionAnswerAdvisor qaAdvisor = QuestionAnswerAdvisor.builder(vectorStore)
        .promptTemplate(customPromptTemplate)
        .build();

    String response = ChatClient.builder(chatModel).build()
        .prompt(question)
        .advisors(qaAdvisor)
        .call()
        .content();
```

|  | The QuestionAnswerAdvisor.Builder.userTextAdvise() method is deprecated in favor of using .promptTemplate() for more flexible customization. |
|---|---|

### [/spring-ai/1.0.4/api_retrieval-augmented-generation.html#_retrievalaugmentationadvisor](/spring-ai/1.0.4/api_retrieval-augmented-generation.html#_retrievalaugmentationadvisor)检索增强顾问

Spring AI 包含一个 [RAG 模块库](/spring-ai/1.0.4/api_retrieval-augmented-generation.html#modules)，您可以使用它来构建自己的 RAG 流。 `RetrievalAugmentationAdvisor` 是一个 `Advisor`,为最常见的 RAG 流提供了开箱即用的实现， 基于模块化架构。[spring-doc.cadn.net.cn](https://spring-doc.cadn.net.cn/)

要使用`RetrievalAugmentationAdvisor`,您需要将`spring-ai-rag`依赖项添加到您的项目中：[spring-doc.cadn.net.cn](https://spring-doc.cadn.net.cn/)

```
<dependency>
   <groupId>org.springframework.ai</groupId>
   <artifactId>spring-ai-rag</artifactId>
</dependency>
```

#### [/spring-ai/1.0.4/api_retrieval-augmented-generation.html#_sequential_rag_flows](/spring-ai/1.0.4/api_retrieval-augmented-generation.html#_sequential_rag_flows)顺序 RAG 流

##### [/spring-ai/1.0.4/api_retrieval-augmented-generation.html#_naive_rag](/spring-ai/1.0.4/api_retrieval-augmented-generation.html#_naive_rag)朴素RAG

```
Advisor retrievalAugmentationAdvisor = RetrievalAugmentationAdvisor.builder()
        .documentRetriever(VectorStoreDocumentRetriever.builder()
                .similarityThreshold(0.50)
                .vectorStore(vectorStore)
                .build())
        .build();

String answer = chatClient.prompt()
        .advisors(retrievalAugmentationAdvisor)
        .user(question)
        .call()
        .content();
```

默认情况下，`RetrievalAugmentationAdvisor` 不允许检索到的上下文为空。当发生这种情况时， 它会指示模型不要回答用户的问题。您可以按如下方式允许空上下文。[spring-doc.cadn.net.cn](https://spring-doc.cadn.net.cn/)

```
Advisor retrievalAugmentationAdvisor = RetrievalAugmentationAdvisor.builder()
        .documentRetriever(VectorStoreDocumentRetriever.builder()
                .similarityThreshold(0.50)
                .vectorStore(vectorStore)
                .build())
        .queryAugmenter(ContextualQueryAugmenter.builder()
                .allowEmptyContext(true)
                .build())
        .build();

String answer = chatClient.prompt()
        .advisors(retrievalAugmentationAdvisor)
        .user(question)
        .call()
        .content();
```

The `VectorStoreDocumentRetriever` accepts a `FilterExpression` to filter the search results based on metadata. You can provide one when instantiating the `VectorStoreDocumentRetriever` or at runtime per request, using the `FILTER_EXPRESSION` advisor context parameter.[spring-doc.cadn.net.cn](https://spring-doc.cadn.net.cn/)

```
Advisor retrievalAugmentationAdvisor = RetrievalAugmentationAdvisor.builder()
        .documentRetriever(VectorStoreDocumentRetriever.builder()
                .similarityThreshold(0.50)
                .vectorStore(vectorStore)
                .build())
        .build();

String answer = chatClient.prompt()
        .advisors(retrievalAugmentationAdvisor)
        .advisors(a -> a.param(VectorStoreDocumentRetriever.FILTER_EXPRESSION, "type == 'Spring'"))
        .user(question)
        .call()
        .content();
```

请参阅 [VectorStoreDocumentRetriever](/spring-ai/1.0.4/api_retrieval-augmented-generation.html#_vectorstoredocumentretriever) 以获取更多信息。[spring-doc.cadn.net.cn](https://spring-doc.cadn.net.cn/)

##### [/spring-ai/1.0.4/api_retrieval-augmented-generation.html#_advanced_rag](/spring-ai/1.0.4/api_retrieval-augmented-generation.html#_advanced_rag)高级RAG

```
Advisor retrievalAugmentationAdvisor = RetrievalAugmentationAdvisor.builder()
        .queryTransformers(RewriteQueryTransformer.builder()
                .chatClientBuilder(chatClientBuilder.build().mutate())
                .build())
        .documentRetriever(VectorStoreDocumentRetriever.builder()
                .similarityThreshold(0.50)
                .vectorStore(vectorStore)
                .build())
        .build();

String answer = chatClient.prompt()
        .advisors(retrievalAugmentationAdvisor)
        .user(question)
        .call()
        .content();
```

您还可以使用 `DocumentPostProcessor` API 在将检索到的文档传递给模型之前对其进行后处理。例如，您可以使用此类接口根据文档与查询的相关性对检索到的文档进行重新排序、删除不相关或冗余的文档，或者压缩每个文档的内容以减少噪声和冗余。[spring-doc.cadn.net.cn](https://spring-doc.cadn.net.cn/)

## [/spring-ai/1.0.4/api_retrieval-augmented-generation.html#modules](/spring-ai/1.0.4/api_retrieval-augmented-generation.html#modules)模块

Spring AI 实现了一种模块化 RAG 架构，该架构的灵感来源于论文中详细阐述的模块化概念 "[模块化 RAG：将 RAG 系统转变为类似乐高积木的可重构框架](https://arxiv.org/abs/2407.21059)"。[spring-doc.cadn.net.cn](https://spring-doc.cadn.net.cn/)

### [/spring-ai/1.0.4/api_retrieval-augmented-generation.html#_pre_retrieval](/spring-ai/1.0.4/api_retrieval-augmented-generation.html#_pre_retrieval)预检索

预检索模块负责处理用户查询，以获得最佳的检索结果。[spring-doc.cadn.net.cn](https://spring-doc.cadn.net.cn/)

#### [/spring-ai/1.0.4/api_retrieval-augmented-generation.html#_query_transformation](/spring-ai/1.0.4/api_retrieval-augmented-generation.html#_query_transformation)查询转换

一个用于将输入查询转换为更有效的检索任务的组件，以应对诸如查询格式不佳、术语歧义、复杂词汇或不支持的语言等挑战。[spring-doc.cadn.net.cn](https://spring-doc.cadn.net.cn/)

|  | 当使用 QueryTransformer 时，建议将 ChatClient.Builder 配置为较低的温度（例如 0.0），以确保结果更具确定性和准确性，从而提升检索质量。大多数聊天模型的默认温度通常偏高，不利于最佳的查询转换，进而导致检索效果下降。 |
|---|---|

##### [/spring-ai/1.0.4/api_retrieval-augmented-generation.html#_compressionquerytransformer](/spring-ai/1.0.4/api_retrieval-augmented-generation.html#_compressionquerytransformer)压缩查询转换器

A `CompressionQueryTransformer` 使用大型语言模型将对话历史和后续查询压缩为一个独立的查询，该查询能够捕捉对话的本质。[spring-doc.cadn.net.cn](https://spring-doc.cadn.net.cn/)

当对话历史较长且后续查询与对话上下文相关时，此转换器非常有用。[spring-doc.cadn.net.cn](https://spring-doc.cadn.net.cn/)

```
Query query = Query.builder()
        .text("And what is its second largest city?")
        .history(new UserMessage("What is the capital of Denmark?"),
                new AssistantMessage("Copenhagen is the capital of Denmark."))
        .build();

QueryTransformer queryTransformer = CompressionQueryTransformer.builder()
        .chatClientBuilder(chatClientBuilder)
        .build();

Query transformedQuery = queryTransformer.transform(query);
```

该组件使用的提示可以通过构建器中可用的`promptTemplate()`方法进行自定义。[spring-doc.cadn.net.cn](https://spring-doc.cadn.net.cn/)

##### [/spring-ai/1.0.4/api_retrieval-augmented-generation.html#_rewritequerytransformer](/spring-ai/1.0.4/api_retrieval-augmented-generation.html#_rewritequerytransformer)重写查询转换器

A `RewriteQueryTransformer` 使用大型语言模型重写用户查询，以在查询目标系统（例如向量存储或网络搜索引擎）时提供更好的结果。[spring-doc.cadn.net.cn](https://spring-doc.cadn.net.cn/)

当用户查询冗长、模糊或包含可能影响搜索结果质量的无关信息时，此转换器非常有用。[spring-doc.cadn.net.cn](https://spring-doc.cadn.net.cn/)

```
Query query = new Query("I'm studying machine learning. What is an LLM?");

QueryTransformer queryTransformer = RewriteQueryTransformer.builder()
        .chatClientBuilder(chatClientBuilder)
        .build();

Query transformedQuery = queryTransformer.transform(query);
```

该组件使用的提示可以通过构建器中可用的`promptTemplate()`方法进行自定义。[spring-doc.cadn.net.cn](https://spring-doc.cadn.net.cn/)

##### [/spring-ai/1.0.4/api_retrieval-augmented-generation.html#_translationquerytransformer](/spring-ai/1.0.4/api_retrieval-augmented-generation.html#_translationquerytransformer)翻译查询转换器

A `TranslationQueryTransformer` 使用大型语言模型将查询翻译为目标语言，该目标语言由用于生成文档嵌入的嵌入模型支持。如果查询已经是目标语言，则原样返回。如果查询的语言未知，则也原样返回。[spring-doc.cadn.net.cn](https://spring-doc.cadn.net.cn/)

当嵌入模型是在特定语言上训练的，而用户查询却使用另一种语言时，此转换器非常有用。[spring-doc.cadn.net.cn](https://spring-doc.cadn.net.cn/)

```
Query query = new Query("Hvad er Danmarks hovedstad?");

QueryTransformer queryTransformer = TranslationQueryTransformer.builder()
        .chatClientBuilder(chatClientBuilder)
        .targetLanguage("english")
        .build();

Query transformedQuery = queryTransformer.transform(query);
```

该组件使用的提示可以通过构建器中可用的`promptTemplate()`方法进行自定义。[spring-doc.cadn.net.cn](https://spring-doc.cadn.net.cn/)

#### [/spring-ai/1.0.4/api_retrieval-augmented-generation.html#_query_expansion](/spring-ai/1.0.4/api_retrieval-augmented-generation.html#_query_expansion)查询扩展

一个用于将输入查询扩展为查询列表的组件，通过提供替代的查询形式或把复杂问题分解为更简单的子查询，来解决诸如查询格式不佳等问题。[spring-doc.cadn.net.cn](https://spring-doc.cadn.net.cn/)

##### [/spring-ai/1.0.4/api_retrieval-augmented-generation.html#_multiqueryexpander](/spring-ai/1.0.4/api_retrieval-augmented-generation.html#_multiqueryexpander)多查询扩展器

A `MultiQueryExpander` 使用大型语言模型将查询扩展为多个语义上多样化的变体 以捕捉不同的视角，这对于检索额外的上下文信息并提高找到相关结果的可能性非常有用。[spring-doc.cadn.net.cn](https://spring-doc.cadn.net.cn/)

```
MultiQueryExpander queryExpander = MultiQueryExpander.builder()
    .chatClientBuilder(chatClientBuilder)
    .numberOfQueries(3)
    .build();
List<Query> queries = queryExpander.expand(new Query("How to run a Spring Boot app?"));
```

默认情况下，`MultiQueryExpander` 会将原始查询包含在展开的查询列表中。您可以通过构建器中的 `includeOriginal` 方法禁用此行为。[spring-doc.cadn.net.cn](https://spring-doc.cadn.net.cn/)

```
MultiQueryExpander queryExpander = MultiQueryExpander.builder()
    .chatClientBuilder(chatClientBuilder)
    .includeOriginal(false)
    .build();
```

该组件使用的提示可以通过构建器中可用的`promptTemplate()`方法进行自定义。[spring-doc.cadn.net.cn](https://spring-doc.cadn.net.cn/)

### [/spring-ai/1.0.4/api_retrieval-augmented-generation.html#_retrieval](/spring-ai/1.0.4/api_retrieval-augmented-generation.html#_retrieval)检索

检索模块负责查询向量存储等数据系统，并检索出最相关的文档。[spring-doc.cadn.net.cn](https://spring-doc.cadn.net.cn/)

#### [/spring-ai/1.0.4/api_retrieval-augmented-generation.html#_document_search](/spring-ai/1.0.4/api_retrieval-augmented-generation.html#_document_search)文档搜索

负责从底层数据源（如搜索引擎、向量存储、数据库或知识图谱）检索`Documents`的组件。[spring-doc.cadn.net.cn](https://spring-doc.cadn.net.cn/)

##### [/spring-ai/1.0.4/api_retrieval-augmented-generation.html#_vectorstoredocumentretriever](/spring-ai/1.0.4/api_retrieval-augmented-generation.html#_vectorstoredocumentretriever)向量存储文档检索器

一个 `VectorStoreDocumentRetriever` 从向量存储中检索与输入查询语义相似的文档。它支持基于元数据、相似度阈值和前 k 个结果的过滤。[spring-doc.cadn.net.cn](https://spring-doc.cadn.net.cn/)

```
DocumentRetriever retriever = VectorStoreDocumentRetriever.builder()
    .vectorStore(vectorStore)
    .similarityThreshold(0.73)
    .topK(5)
    .filterExpression(new FilterExpressionBuilder()
        .eq("genre", "fairytale")
        .build())
    .build();
List<Document> documents = retriever.retrieve(new Query("What is the main character of the story?"));
```

筛选表达式可以是静态的或动态的。对于动态筛选表达式，您可以传递一个 `Supplier`。[spring-doc.cadn.net.cn](https://spring-doc.cadn.net.cn/)

```
DocumentRetriever retriever = VectorStoreDocumentRetriever.builder()
    .vectorStore(vectorStore)
    .filterExpression(() -> new FilterExpressionBuilder()
        .eq("tenant", TenantContextHolder.getTenantIdentifier())
        .build())
    .build();
List<Document> documents = retriever.retrieve(new Query("What are the KPIs for the next semester?"));
```

您还可以通过`Query` API，使用`FILTER_EXPRESSION`参数提供请求特定的筛选表达式。 如果同时提供了请求特定和检索器特定的筛选表达式，则请求特定的筛选表达式优先生效。[spring-doc.cadn.net.cn](https://spring-doc.cadn.net.cn/)

```
Query query = Query.builder()
    .text("Who is Anacletus?")
    .context(Map.of(VectorStoreDocumentRetriever.FILTER_EXPRESSION, "location == 'Whispering Woods'"))
    .build();
List<Document> retrievedDocuments = documentRetriever.retrieve(query);
```

#### [/spring-ai/1.0.4/api_retrieval-augmented-generation.html#_document_join](/spring-ai/1.0.4/api_retrieval-augmented-generation.html#_document_join)文档连接

一个用于将基于多个查询和来自多个数据源检索到的文档合并为单个文档集合的组件。作为连接过程的一部分，它还可以处理重复文档和相互排名策略。[spring-doc.cadn.net.cn](https://spring-doc.cadn.net.cn/)

##### [/spring-ai/1.0.4/api_retrieval-augmented-generation.html#_concatenationdocumentjoiner](/spring-ai/1.0.4/api_retrieval-augmented-generation.html#_concatenationdocumentjoiner)连接文档连接器

一个 `ConcatenationDocumentJoiner` 通过将基于多个查询和多个数据源检索到的文档连接成一个单一的文档集合来合并这些文档。如果出现重复文档，则保留首次出现的文档。 每个文档的分数保持不变。[spring-doc.cadn.net.cn](https://spring-doc.cadn.net.cn/)

```
Map<Query, List<List<Document>>> documentsForQuery = ...
DocumentJoiner documentJoiner = new ConcatenationDocumentJoiner();
List<Document> documents = documentJoiner.join(documentsForQuery);
```

### [/spring-ai/1.0.4/api_retrieval-augmented-generation.html#_post_retrieval](/spring-ai/1.0.4/api_retrieval-augmented-generation.html#_post_retrieval)检索后

检索后模块负责处理检索到的文档，以实现最佳的生成结果。[spring-doc.cadn.net.cn](https://spring-doc.cadn.net.cn/)

#### [/spring-ai/1.0.4/api_retrieval-augmented-generation.html#_document_post_processing](/spring-ai/1.0.4/api_retrieval-augmented-generation.html#_document_post_processing)文档后处理

一个用于基于查询对检索到的文档进行后处理的组件，以解决诸如*中间丢失*、模型的上下文长度限制以及减少检索信息中的噪声和冗余等问题。[spring-doc.cadn.net.cn](https://spring-doc.cadn.net.cn/)

例如，它可以根据文档与查询的相关性对文档进行排序，删除不相关或冗余的文档，或者压缩每个文档的内容以减少噪声和冗余。[spring-doc.cadn.net.cn](https://spring-doc.cadn.net.cn/)

### [/spring-ai/1.0.4/api_retrieval-augmented-generation.html#_generation](/spring-ai/1.0.4/api_retrieval-augmented-generation.html#_generation)生成

生成模块负责根据用户查询和检索到的文档生成最终响应。[spring-doc.cadn.net.cn](https://spring-doc.cadn.net.cn/)

#### [/spring-ai/1.0.4/api_retrieval-augmented-generation.html#_query_augmentation](/spring-ai/1.0.4/api_retrieval-augmented-generation.html#_query_augmentation)查询增强

一个用于为输入查询添加附加数据的组件，有助于为大型语言模型提供回答用户查询所需的上下文。[spring-doc.cadn.net.cn](https://spring-doc.cadn.net.cn/)

##### [/spring-ai/1.0.4/api_retrieval-augmented-generation.html#_contextualqueryaugmenter](/spring-ai/1.0.4/api_retrieval-augmented-generation.html#_contextualqueryaugmenter)上下文查询增强器

`ContextualQueryAugmenter` 通过从所提供文档的内容中提取上下文数据来增强用户查询。[spring-doc.cadn.net.cn](https://spring-doc.cadn.net.cn/)

```
QueryAugmenter queryAugmenter = ContextualQueryAugmenter.builder().build();
```

默认情况下，`ContextualQueryAugmenter` 不允许检索到的上下文为空。当发生这种情况时， 它会指示模型不要回答用户的查询。[spring-doc.cadn.net.cn](https://spring-doc.cadn.net.cn/)

您可以启用 `allowEmptyContext` 选项，以便在检索到的上下文为空时，模型仍能生成响应。[spring-doc.cadn.net.cn](https://spring-doc.cadn.net.cn/)

```
QueryAugmenter queryAugmenter = ContextualQueryAugmenter.builder()
        .allowEmptyContext(true)
        .build();
```

此组件使用的提示可以通过构建器中提供的 `promptTemplate()` 和 `emptyContextPromptTemplate()` 方法进行自定义。[spring-doc.cadn.net.cn](https://spring-doc.cadn.net.cn/)
