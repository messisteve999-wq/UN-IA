package com.steve.un;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private EditText messageInput, serverInput;
    private TextView chatView, statusView;
    private ScrollView scroll;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private android.content.SharedPreferences prefs;
    private JSONArray history = new JSONArray();

    private final String localWelcome =
        "Bonjour ! Je suis UN. Je peux discuter avec toi et conserver " +
        "l'historique de cette conversation sur ton téléphone.";

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("un_memory", MODE_PRIVATE);
        history = loadHistory();
        buildInterface();
        renderHistory();
        if (history.length() == 0) addMessage("UN", localWelcome);
    }

    private void buildInterface() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(16, 12, 16, 12);
        root.setBackgroundColor(Color.rgb(247, 246, 252));

        TextView title = new TextView(this);
        title.setText("UN");
        title.setTextSize(28);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(Color.rgb(39, 32, 82));
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Ton assistant personnel • Mémoire locale");
        subtitle.setTextSize(13);
        root.addView(subtitle);

        serverInput = new EditText(this);
        serverInput.setSingleLine(true);
        serverInput.setTextSize(12);
        serverInput.setHint("Adresse du serveur, ex. https://mon-service.onrender.com");
        serverInput.setText(prefs.getString("server", ""));
        serverInput.setInputType( android.text.InputType.TYPE_CLASS_TEXT
            | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        root.addView(serverInput);

        statusView = new TextView(this);
        statusView.setText("Prêt • Sans serveur, UN utilise le mode local.");
        statusView.setTextSize(12);
        root.addView(statusView);

        scroll = new ScrollView(this);
        chatView = new TextView(this);
        chatView.setTextSize(16);
        chatView.setTextColor(Color.rgb(35, 35, 45));
        chatView.setPadding(8, 12, 8, 12);
        scroll.addView(chatView);

        root.addView(scroll, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        messageInput = new EditText(this);
        messageInput.setHint("Écris un message à UN...");
        messageInput.setMinLines(1);
        messageInput.setMaxLines(4);
        messageInput.setImeOptions(EditorInfo.IME_ACTION_SEND);
        root.addView(messageInput);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);

        Button send = new Button(this);
        send.setText("Envoyer");
        buttons.addView(send, new LinearLayout.LayoutParams(0,
            LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        send.setOnClickListener(v -> sendMessage());

        Button clear = new Button(this);
        clear.setText("Effacer");
        buttons.addView(clear, new LinearLayout.LayoutParams(0,
            LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        clear.setOnClickListener(v -> {
            history = new JSONArray();
            saveHistory();
            chatView.setText("");
            addMessage("UN", "Mémoire de conversation effacée.");
        });

        root.addView(buttons);
        setContentView(root);
    }

    private JSONArray loadHistory() {
        try {
            return new JSONArray(prefs.getString("history", "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    private void saveHistory() {
        prefs.edit()
            .putString("history", history.toString())
            .putString("server", serverInput == null ? "" :
                serverInput.getText().toString().trim())
            .apply();
    }

    private void addMessage(String who, String text) {
        try {
            JSONObject item = new JSONObject();
            item.put("role", who.equals("Moi") ? "user" : "assistant");
            item.put("name", who);
            item.put("text", text);
            history.put(item);
            saveHistory();
            renderHistory();
        } catch (Exception ignored) {}
    }

    private void renderHistory() {
        if (chatView == null) return;
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < history.length(); i++) {
            JSONObject item = history.optJSONObject(i);
            if (item == null) continue;
            out.append(item.optString("name", "UN"))
               .append(" :\n")
               .append(item.optString("text", ""))
               .append("\n\n");
        }
        chatView.setText(out.toString());
        if (scroll != null) scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }

    private void sendMessage() {
        String message = messageInput.getText().toString().trim();
        if (message.isEmpty()) return;

        messageInput.setText("");
        addMessage("Moi", message);

        String endpoint = serverInput.getText().toString().trim();
        prefs.edit().putString("server", endpoint).apply();

        if (!endpoint.startsWith("https://")) {
            addMessage("UN", localReply(message));
            statusView.setText("Mode local • Aucun serveur configuré.");
            return;
        }

        statusView.setText("UN réfléchit...");

        JSONArray context = new JSONArray();
        int start = Math.max(0, history.length() - 12);
        for (int i = start; i < history.length(); i++) {
            JSONObject item = history.optJSONObject(i);
            if (item != null) context.put(item);
        }

        executor.execute(() -> {
            String reply;
            try {
                JSONObject body = new JSONObject();
                body.put("message", message);
                body.put("history", context);

                URL url = new URL(endpoint.replaceAll("/+$", "") + "/chat");
                HttpURLConnection connection =
                    (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(12000);
                connection.setReadTimeout(20000);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json");

                try (OutputStream os = connection.getOutputStream()) {
                    os.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }

                int code = connection.getResponseCode();
                InputStream stream = code >= 200 && code < 300
                    ? connection.getInputStream() : connection.getErrorStream();

                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                if (stream != null) {
                    try (InputStream in = stream) {
                        byte[] buffer = new byte[1024];
                        int n;
                        while ((n = in.read(buffer)) != -1) bytes.write(buffer, 0, n);
                    }
                }
                if (code < 200 || code >= 300) {
                    throw new IOException("HTTP " + code);
                }

                JSONObject response = new JSONObject(
                    bytes.toString(StandardCharsets.UTF_8.name()));
                reply = response.optString("reply", "Je n'ai pas de réponse.");
                connection.disconnect();
                runOnUiThread(() -> statusView.setText("Serveur connecté."));
            } catch (Exception e) {
                reply = localReply(message);
                runOnUiThread(() -> statusView.setText(
                    "Serveur indisponible • Réponse locale."));
            }

            final String answer = reply;
            runOnUiThread(() -> addMessage("UN", answer));
        });
    }

    private String localReply(String message) {
        String m = message.toLowerCase(java.util.Locale.ROOT);
        if (m.matches(".*\\b(bonjour|salut|bonsoir|hello|coucou)\\b.*"))
            return "Salut ! Comment se passe ta journée ?";
        if (m.contains("ton nom") || m.contains("qui es-tu"))
            return "Je suis UN, ton assistant personnel en développement.";
        if (m.contains("merci"))
            return "Avec plaisir ! Que veux-tu faire ensuite ?";
        if (m.contains("aide") || m.contains("que peux-tu faire"))
            return "Je peux discuter, garder l'historique sur ton téléphone et " +
                   "utiliser le serveur UN lorsqu'il est configuré.";
        if (m.contains("triste") || m.contains("ça va pas"))
            return "Je suis là pour t'écouter. Veux-tu me raconter ce qui se passe ?";
        return "J'ai bien reçu ton message : « " + message +
               " ». Mon petit moteur local est encore limité. " +
               "Configure le serveur UN pour essayer le modèle expérimental.";
    }

    @Override
    protected void onDestroy() {
        executor.shutdown();
        super.onDestroy();
    }
}
