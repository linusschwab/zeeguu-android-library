package ch.unibe.zeeguulibrary.Core;

import android.app.Activity;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.util.Log;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import ch.unibe.R;
import ch.unibe.zeeguulibrary.MyWords.MyWordsHeader;
import ch.unibe.zeeguulibrary.MyWords.MyWordsInfoHeader;
import ch.unibe.zeeguulibrary.MyWords.MyWordsItem;

/**
 * Class to connect with the Zeeguu API
 */
public class ZeeguuConnectionManager {

    private final String URL = "https://www.zeeguu.unibe.ch/";
    private RequestQueue queue;

    private ZeeguuAccount account;
    private Activity activity;
    private String selection, translation;
    private ZeeguuConnectionManagerCallbacks callback;

    /**
     * Callback interface that must be implemented by the container activity
     */
    public interface ZeeguuConnectionManagerCallbacks {
        void showZeeguuLoginDialog(String title, String email);
        void showZeeguuCreateAccountDialog(String recallUsername, String recallEmail);
        void setTranslation(String translation);
        void displayErrorMessage(String error, boolean isToast);
        void displayMessage(String message);
        void highlight(String word);
    }

    public ZeeguuConnectionManager(Activity activity) {
        this.account = new ZeeguuAccount(activity);
        this.activity = activity;

        // Make sure that the interface is implemented in the container activity
        try {
            callback = (ZeeguuConnectionManagerCallbacks) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException("Activity must implement ZeeguuConnectionManagerCallbacks");
        }

        queue = Volley.newRequestQueue(activity);

        // Load user information
        account.load();

        // Get missing information from server
        if (!account.isUserLoggedIn())
            callback.showZeeguuLoginDialog("", "");
        else if (!account.isUserInSession())
            getSessionId(account.getEmail(), account.getPassword());
        else if (!account.isLanguageSet())
            getUserLanguages();
    }


    public void createAccountOnServer(final String username, final String email, final String pw) {
        String url_create_account = URL + "add_user/" + email;

        StringRequest request = new StringRequest(Request.Method.POST,
                url_create_account, new Response.Listener<String>() {

            @Override
            public void onResponse(String response) {
                account.setEmail(email);
                account.setPassword(pw);
                account.setSessionID(response);
                account.saveLoginInformation();
                callback.displayMessage(activity.getString(R.string.login_successful));
                getUserLanguages();
                getMyWordsFromServer();
            }
        }, new Response.ErrorListener() {

            @Override
            public void onErrorResponse(VolleyError error) {
                Log.e("create_acc", error.getMessage());
                callback.showZeeguuCreateAccountDialog(username, email);
            }
        }) {

            @Override
            protected Map<String, String> getParams() {
                Map<String, String> params = new HashMap<>();
                params.put("username", username);
                params.put("password", pw);
                return params;
            }
        };

        queue.add(request);
    }

    /**
     * Gets a session ID which is needed to use the API
     */
    public void getSessionId(final String email, String password) {
        if (!isNetworkAvailable())
            return; // ignore here

        account.setEmail(email);
        account.setPassword(password);

        String urlSessionID = URL + "session/" + email;

        StringRequest request = new StringRequest(Request.Method.POST,
                urlSessionID, new Response.Listener<String>() {

            @Override
            public void onResponse(String response) {
                account.setSessionID(response);
                account.saveLoginInformation();
                callback.displayMessage(activity.getString(R.string.login_successful));
                getUserLanguages();
                getMyWordsFromServer();
            }
        }, new Response.ErrorListener() {

            @Override
            public void onErrorResponse(VolleyError error) {
                account.setEmail("");
                account.setPassword("");
                callback.showZeeguuLoginDialog(activity.getString(R.string.login_zeeguu_error_wrong), email);
            }
        }) {

            @Override
            protected Map<String, String> getParams() {
                Map<String, String> params = new HashMap<>();
                params.put("password", account.getPassword());
                return params;
            }
        };

        queue.add(request);
    }

    /**
     * Translates a given word or phrase from a language to another language
     *
     * @Precondition: user needs to be logged in and have a session id
     */
    public void translate(final String input, String inputLanguageCode, String outputLanguageCode) {
        if (!account.isUserLoggedIn()) {
            callback.displayErrorMessage(activity.getString(R.string.no_login), false);
            return;
        } else if (!isNetworkAvailable()) {
            callback.displayErrorMessage(activity.getString(R.string.no_internet_connection), false);
            return;
        } else if (!account.isUserInSession()) {
            getSessionId(account.getEmail(), account.getPassword());
            return;
        } else if (!isInputValid(input))
            return; // ignore
        else if (isSameLanguage(inputLanguageCode, outputLanguageCode)) {
            callback.displayErrorMessage(activity.getString(R.string.error_language), false);
            return;
        } else if (isSameSelection(input)) {
            if (translation != null)
                callback.setTranslation(translation);
            return;
        }

        selection = input;

        // /translate/<from_lang_code>/<to_lang_code>
        String urlTranslation = URL + "translate/" + inputLanguageCode + "/" + outputLanguageCode +
                "?session=" + account.getSessionID();

        StringRequest request = new StringRequest(Request.Method.POST,
                urlTranslation, new Response.Listener<String>() {

            @Override
            public void onResponse(String response) {
                if (response != null)
                    callback.setTranslation(response);
                translation = response;
            }

        }, new Response.ErrorListener() {

            @Override
            public void onErrorResponse(VolleyError error) {
                // TODO: handle error responses
                Log.e("translation", error.toString());
            }
        }) {

            @Override
            protected Map<String, String> getParams() {
                Map<String, String> params = new HashMap<>();
                params.put("word", Uri.encode(input.trim()));
                params.put("url", "");
                params.put("context", "");

                return params;
            }
        };

        // TODO: Set tag and cancel all older translations
        queue.add(request);
    }

    public void bookmarkWithContext(String input, String fromLanguageCode, String translation, String toLanguageCode,
                                    final String title, final String url, final String context) {
        if (!account.isUserLoggedIn()) {
            callback.showZeeguuLoginDialog("", "");
            return;
        } else if (!isNetworkAvailable() || !isInputValid(input) || !isInputValid(translation))
            return;
        else if (!account.isUserInSession()) {
            getSessionId(account.getEmail(), account.getPassword());
            return;
        }

        callback.highlight(input);

        // /bookmark_with_context/<from_lang_code>/<term>/<to_lang_code>/<translation>
        String urlContribution = URL + "bookmark_with_context/" + fromLanguageCode + "/" + Uri.encode(input.trim()) + "/" +
                toLanguageCode + "/" + Uri.encode(translation) + "?session=" + account.getSessionID();

        StringRequest request = new StringRequest(Request.Method.POST,
                urlContribution, new Response.Listener<String>() {

            @Override
            public void onResponse(String response) {
                callback.displayMessage("Word saved to your wordlist");
            }

        }, new Response.ErrorListener() {

            @Override
            public void onErrorResponse(VolleyError error) {
                callback.displayMessage("Something went wrong. Please try again.");
                Log.e("bookmark_with_context", error.toString());
            }
        }) {

            @Override
            protected Map<String, String> getParams() {
                Map<String, String> params = new HashMap<>();
                params.put("title", title);
                params.put("url", url);
                params.put("context", context);

                return params;
            }
        };

        queue.add(request);
    }

    private void getUserLanguages() {
        if (!account.isUserLoggedIn() || !isNetworkAvailable()) {
            return;
        } else if (!account.isUserInSession()) {
            getSessionId(account.getEmail(), account.getPassword());
            return;
        }

        String urlLanguage = URL + "learned_and_native_language" + "?session=" + account.getSessionID();

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, urlLanguage,
                new Response.Listener<JSONObject>() {

                    @Override
                    public void onResponse(JSONObject response) {
                        try {
                            account.setLanguageNative(response.getString("native"));
                            account.setLanguageLearning(response.getString("learned"));
                            account.saveLanguages();
                        } catch (JSONException error) {
                            Log.e("get_user_language", error.toString());
                        }
                    }

                }, new Response.ErrorListener() {

            @Override
            public void onErrorResponse(VolleyError error) {
                Log.e("get_user_language", error.toString());
            }
        });

        queue.add(request);
    }

    public void getMyWordsFromServer() {
        if (!account.isUserInSession()) {
            return;
        } else if (!isNetworkAvailable()) {
            account.myWordsLoadFromPhone();
            return;
        }

        String url_session_ID = URL + "bookmarks_by_day/with_context?session=" + account.getSessionID();

        JsonArrayRequest request = new JsonArrayRequest(url_session_ID, new Response.Listener<JSONArray>() {

            @Override
            public void onResponse(JSONArray allBookmarks) {
                ArrayList<MyWordsHeader> myWords = account.getMyWords();
                myWords.clear();

                //ToDo: optimization that not everytime the whole list is sent
                try {
                    for (int j = 0; j < allBookmarks.length(); j++) {
                        JSONObject bookmark = allBookmarks.getJSONObject(j);
                        MyWordsHeader header = new MyWordsHeader(bookmark.getString("date"));
                        myWords.add(header);
                        JSONArray bookmarks = bookmark.getJSONArray("bookmarks");
                        String title = "";

                        for (int i = 0; i < bookmarks.length(); i++) {
                            JSONObject translation = bookmarks.getJSONObject(i);
                            //add title when a new one is
                            if (!title.equals(translation.getString("title"))) {
                                title = translation.getString("title");
                                header.addChild(new MyWordsInfoHeader(title));
                            }
                            //add word as entry to list
                            int id = translation.getInt("id");
                            String languageFromWord = translation.getString("from");
                            String languageFrom = translation.getString("from_language");
                            String languageToWord = translation.getString("to");
                            String languageTo = translation.getString("to_language");
                            String context = translation.getString("context");

                            header.addChild(new MyWordsItem(id, languageFromWord, languageToWord, context, languageFrom, languageTo));
                        }
                    }

                    account.setMyWords(myWords);
                    callback.displayMessage(activity.getString(R.string.successful_mywords_updated));
                } catch (JSONException error) {
                    Log.e("get_my_words", error.toString());
                }

            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.e("get_my_words", error.toString());
            }
        });

        queue.add(request);
    }

    public void removeBookmarkFromServer(long bookmarkID) {
        if (!account.isUserInSession() || !isNetworkAvailable())
            return;

        String urlRemoveBookmark = URL + "delete_contribution/" + bookmarkID + "?session=" + account.getSessionID();

        StringRequest request = new StringRequest(Request.Method.POST,
                urlRemoveBookmark, new Response.Listener<String>() {

            @Override
            public void onResponse(String response) {
                if (response.equals("OK")) {
                    callback.displayMessage(activity.getString(R.string.successful_bookmark_deleted));
                } else {
                    callback.displayErrorMessage(activity.getString(ch.unibe.R.string.error_bookmark_delete), true);
                }
            }
        }, new Response.ErrorListener() {

            @Override
            public void onErrorResponse(VolleyError error) {
                Log.e("remove_bookmark", error.toString());
            }

        });

        queue.add(request);
    }

    // Boolean Checks
    // TODO: Write tests!
    public boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) activity.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        return activeNetwork != null && activeNetwork.isConnected();
    }

    private boolean isInputValid(String input) {
        return !(input == null || input.trim().equals(""));
    }

    private boolean isSameSelection(String input) {
        return input.equals(selection);
    }

    private boolean isSameLanguage(String inputLanguageCode, String translationLanguageCode) {
        return inputLanguageCode.equals(translationLanguageCode);
    }

    // Getters and Setters
    public ZeeguuAccount getAccount() {
        return account;
    }

    public void setAccount(ZeeguuAccount account) {
        this.account = account;
    }
}