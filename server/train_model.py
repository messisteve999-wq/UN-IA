import json
import random
import re
from collections import defaultdict
from pathlib import Path

BASE = Path(__file__).resolve().parent
CORPUS = BASE / "corpus.txt"
MODEL = BASE / "model.json"

def train():
    transitions = defaultdict(list)
    starts = []

    for line in CORPUS.read_text(encoding="utf-8").splitlines():
        words = re.findall(r"\w+|[.,!?;]", line.lower(), flags=re.UNICODE)
        if not words:
            continue
        starts.append(words[0])
        sequence = ["<s>"] + words + ["</s>"]
        for a, b in zip(sequence, sequence[1:]):
            transitions[a].append(b)

    model = {
        "starts": starts,
        "transitions": dict(transitions),
        "description": "UN tiny word-bigram Markov model trained from corpus.txt",
    }
    MODEL.write_text(
        json.dumps(model, ensure_ascii=False),
        encoding="utf-8"
    )
    print("Modèle entraîné et enregistré :", MODEL)
    print("Nombre de mots de départ :", len(starts))
    print("États appris :", len(transitions))

if __name__ == "__main__":
    random.seed(42)
    train()
