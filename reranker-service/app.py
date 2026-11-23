from fastapi import FastAPI
from pydantic import BaseModel
from sentence_transformers import CrossEncoder

app = FastAPI()

MODEL_NAME = "BAAI/bge-reranker-v2-m3"

print("Loading reranker model...")

model = CrossEncoder(
    MODEL_NAME,
    max_length=512
)

print("Reranker model loaded successfully!")


class RerankRequest(BaseModel):
    question: str
    documents: list[str]


@app.post("/rerank")
def rerank(request: RerankRequest):

    pairs = [
        (request.question, document)
        for document in request.documents
    ]

    scores = model.predict(pairs)

    results = []

    for index, score in enumerate(scores):
        results.append({
            "documentIndex": index,
            "score": float(score),
            "content": request.documents[index]
        })

    results.sort(
        key=lambda item: item["score"],
        reverse=True
    )

    return {
        "results": results
    }
