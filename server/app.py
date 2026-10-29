import random
import re
from pathlib import Path

from flask import Flask, jsonify, request
from train_model import train

BASE = Path(__file__).resolve().parent
MODEL_PATH = BASE / "model.json"

app = Flask(__name__)

def load_model():
    if not MODEL_PATH.exists():
        train()
    import json
    return json.loads(MODEL_PATH.read_text(encoding="utf-8"))

model = load_model()

def normalize(text):
    return text.lower().strip()

def generate_markov(max_words=24):
    transitions = model.get("transitions", {})
    starts = model.get("starts", [])
    if not starts:
        return "Je suis encore en apprentissage."

    word = random.choice(starts)
    output = [word]

    for _ in range(max_words - 1):
        options = transitions.get(word, [])
        if not options:
            break
        word = random.choice(options)
        if word == "</s>":
            break
        output.append(word)

    text = " ".join(output)
    text = re.sub(r"\s+([.,!?;])", r"\1", text)
    return text[:350]

def answer(message, history):
    text = normalize(message)

    if any(x in text for x in ["bonjour", "salut", "bonsoir", "coucou", "hello"]):
        return "Salut ! Je suis UN. Comment se passe ta journée ?"

    if "ton nom" in text or "qui es-tu" in text:
        return "Je m'appelle UN. Je suis un assistant expérimental avec une mémoire locale sur ton téléphone."

    if "merci" in text:
        return "Avec plaisir ! Que veux-tu explorer maintenant ?"

    if any(x in text for x in ["triste", "seul", "ça ne va pas", "ca ne va pas"]):
        return "Je t'écoute. Veux-tu me raconter ce qui te préoccupe ?"

    if any(x in text for x in ["aide", "fonction", "que peux-tu faire"]):
        return ("Je peux répondre à quelques intentions, tester mon petit modèle "
                "de langage et conserver l'historique dans l'application Android.")

    generated = generate_markov()
    return ("Je suis encore un petit modèle expérimental. "
            "Voici une phrase générée à partir de mon corpus : " + generated)

@app.get("/")
def home():
    return jsonify({
        "name": "UN",
        "status": "online",
        "model": "tiny word-bigram Markov",
        "notice": "Prototype expérimental"
    })

@app.get("/health")
def health():
    return jsonify({"status": "ok"})

@app.post("/chat")
def chat():
    data = request.get_json(silent=True) or {}
    message = str(data.get("message", "")).strip()
    history = data.get("history", [])

    if not message:
        return jsonify({"error": "Le message est vide."}), 400

    if not isinstance(history, list):
        history = []

    reply = answer(message[:2000], history[-12:])
    return jsonify({
        "reply": reply,
        "model": "UN-mini-v1",
        "history_received": min(len(history), 12)
    })

if __name__ == "__main__":
    app.run(host="0.0.0.0", port=10000)
