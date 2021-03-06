package com.qsoft.pilotproject.ui.activity;

import android.accounts.Account;
import android.accounts.AccountAuthenticatorActivity;
import android.accounts.AccountManager;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import com.example.PilotProject.R;
import com.googlecode.androidannotations.annotations.*;
import com.qsoft.pilotproject.authenticator.ApplicationAccountManager;
import com.qsoft.pilotproject.authenticator.OnlineDioAuthenticator;
import com.qsoft.pilotproject.common.CommandExecutor;
import com.qsoft.pilotproject.common.commands.GenericStartActivityCommand;
import com.qsoft.pilotproject.handler.AuthenticatorHandler;
import com.qsoft.pilotproject.handler.impl.AuthenticatorHandlerImpl;
import com.qsoft.pilotproject.model.dto.SignInDTO;
import com.qsoft.pilotproject.utils.Utilities;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;

import java.io.IOException;

/**
 * User: binhtv
 * Date: 10/14/13
 * Time: 2:34 PM
 */
@EActivity(R.layout.activity_login)
public class LoginActivity extends AccountAuthenticatorActivity
{
    private static final int RC_LAUCH_ACTIVITY = 1;
    private static final int RC_SLIDE_BAR_ACTIVITY = 2;
    @Bean(AuthenticatorHandlerImpl.class)
    public AuthenticatorHandler onLineDioService;

    static final String TAG = "LoginActivity";
    static final String ERROR_MESSAGE = "Error_Message";
    static final String ACCESS_TOKEN = "ACCESS_TOKEN";
    static final String AUTHTOKEN_TYPE_FULL_ACCESS = "token_type";
    static final String KEY_USER_PASSWORD = "user_pass";
    public static final String USER_ID_KEY = "user_id";

    @ViewById(R.id.login_ivLogin)
    ImageView imDone;

    @ViewById(R.id.login_ivBack)
    ImageView imBack;

    @ViewById(R.id.login_etMail)
    EditText etEmail;

    @ViewById(R.id.login_etPassword)
    EditText etPassword;

    @ViewById(R.id.login_tvForgotPass)
    TextView forgotPass;
    @Bean
    CommandExecutor commandExecutor;

    final String EMAIL_PATTERN =
            "^[_A-Za-z0-9-\\+]+(\\.[_A-Za-z0-9-]+)*@"
                    + "[A-Za-z0-9-]+(\\.[A-Za-z0-9]+)*(\\.[A-Za-z]{2,})$";

    @SystemService
    AccountManager accountManager;

    String authTokenType;

    @Bean
    ApplicationAccountManager applicationAccountManager;

    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
    }

    @AfterViews
    void afterViews()
    {
        authTokenType = getIntent().getStringExtra(OnlineDioAuthenticator.AUTH_TYPE_KEY);
        if (authTokenType == null)
        {
            authTokenType = AUTHTOKEN_TYPE_FULL_ACCESS;
        }
    }

    @AfterTextChange({R.id.login_etMail, R.id.login_etPassword})
    void handleTextChangeEmail()
    {
        if (etEmail.getText().toString().isEmpty() || etPassword.getText().toString().isEmpty())
        {
            imDone.setBackgroundDrawable(getResources().getDrawable(R.drawable.login_btdone_invisible));
            imDone.setClickable(false);
        }
        else
        {
            imDone.setBackgroundDrawable(getResources().getDrawable(R.drawable.login_btdone));
            imDone.setClickable(true);
        }
    }

    @Click(R.id.login_ivBack)
    void doClickBack()
    {

        commandExecutor.execute(this,
                new GenericStartActivityCommand(this, LaunchActivity_.class, RC_LAUCH_ACTIVITY)
                {
                    @Override
                    public void overrideExtra(Intent intent)
                    {
                    }
                }, false);

        Log.d(TAG, "come back to launch screen");
    }

    @Click(R.id.login_ivLogin)
    void doClickLogin()
    {
        if (isOnlineNetwork() && validateMailAndPassword(etEmail, etPassword))
        {
//
            final String email = etEmail.getText().toString();
            final String pass = Utilities.stringToMD5(etPassword.getText().toString());
            final String accountType = getIntent().getStringExtra(OnlineDioAuthenticator.ACCOUNT_TYPE_KEY);
            new AsyncTask<String, Void, Intent>()
            {

                @Override
                protected Intent doInBackground(String... strings)
                {
                    Log.d(TAG, "started authenticating ...");
                    Bundle data = new Bundle();
                    try
                    {
                        SignInDTO signInDTO = onLineDioService.signIn(email, pass, authTokenType);
                        if (signInDTO == null)
                        {
                            throw new Exception();
                        }
                        data.putLong(USER_ID_KEY, Long.valueOf(signInDTO.getUserId()));
                        data.putString(AccountManager.KEY_AUTHTOKEN, signInDTO.getAccessToken());
                        data.putString(AccountManager.KEY_ACCOUNT_NAME, email);
                        data.putString(AccountManager.KEY_ACCOUNT_TYPE, accountType);
                        data.putString(KEY_USER_PASSWORD, pass);
                    }
                    catch (Exception e)
                    {
                        data.putString(ERROR_MESSAGE, "User and password are incorrect!");
                    }
                    final Intent res = new Intent();
                    res.putExtras(data);
                    return res;
                }

                @Override
                protected void onPostExecute(Intent intent)
                {
                    if (intent.hasExtra(ERROR_MESSAGE))
                    {
                        Toast.makeText(getBaseContext(), intent.getStringExtra(ERROR_MESSAGE), Toast.LENGTH_LONG).show();
                    }
                    else
                    {
                        finishLogin(intent);
                    }
                }
            }.execute();
        }
    }


    void finishLogin(Intent intent)
    {
        Log.d(TAG, "finishLogin(intent)");
        String accountName = intent.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
        String accountPassword = intent.getStringExtra(KEY_USER_PASSWORD);
        Account account = new Account(accountName, intent.getStringExtra(AccountManager.KEY_ACCOUNT_TYPE));
        if (getIntent().getBooleanExtra(OnlineDioAuthenticator.IS_ADDED_ACCOUNT_KEY, false))
        {
            Log.d(TAG, "finishLogin > addAccountExplicitly");
            String authToken = intent.getStringExtra(AccountManager.KEY_AUTHTOKEN);
            Bundle bundle = new Bundle();
            bundle.putString(USER_ID_KEY, Long.toString(intent.getLongExtra(USER_ID_KEY, 0)));
            accountManager.addAccountExplicitly(account, accountPassword, bundle);
            applicationAccountManager.setAccount(account);
            applicationAccountManager.setTokenAuth(authToken);
            accountManager.setAuthToken(account, authTokenType, authToken);
        }
        else
        {
            Log.d(TAG, "finish Login > set password");
            accountManager.setPassword(account, accountPassword);
        }

        setAccountAuthenticatorResult(intent.getExtras());
        applicationAccountManager.setAccount(account);
        commandExecutor.execute(this,
                new GenericStartActivityCommand(this, SlideBarActivity_.class, RC_SLIDE_BAR_ACTIVITY)
                {
                    @Override
                    public void overrideExtra(Intent intent)
                    {
                    }
                }, false);

        Log.d(TAG, "Login successfully");
    }

    @Click(R.id.login_tvForgotPass)
    void doClickForgetPassword()
    {
        AlertDialog dialog = showAlertDialogResetPassword("Forgot Password", "To reset your password, please enter your" +
                " email address");
    }

    boolean isOnlineNetwork()
    {
        // checkTimeoutService();
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        if (netInfo != null && netInfo.isConnectedOrConnecting())
        {
            Log.d(TAG, "network available");
            return true;
        }
        else
        {
            AlertDialog dialog = showAlertDialog("Error Signing In", "There is no connection to the internet.");
            dialog.show();
            Log.d(TAG, "network no connection");
            return false;
        }
    }

    void checkTimeoutService()
    {
        HttpGet httpGet = new HttpGet("http://www.google.com");
        HttpParams httpParameters = new BasicHttpParams();
        int timeoutConnection = 15000;
        HttpConnectionParams.setConnectionTimeout(httpParameters, timeoutConnection);
        int timeoutSocket = 15000;
        HttpConnectionParams.setSoTimeout(httpParameters, timeoutSocket);
        DefaultHttpClient httpClient = new DefaultHttpClient(httpParameters);
        try
        {
            Log.d(TAG, "Checking connection...");
            httpClient.execute(httpGet);
            Log.d(TAG, "request service successfully");
            return;
        }
        catch (ClientProtocolException e)
        {
            e.printStackTrace();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        Log.d(TAG, "Connection timeout");
    }

    boolean validateMailAndPassword(EditText mail, EditText password)
    {
        String _mail = mail.getText().toString();
        String _password = password.getText().toString();
        if (_mail.matches(EMAIL_PATTERN) == false)
        {
            AlertDialog dialog = showAlertDialog("Error Signing In", "Email address is incorrect.");
            dialog.show();
            mail.requestFocus();
            return false;
        }
        else if (_password.length() <= 0)
        {
            AlertDialog dialog = showAlertDialog("Error Signing In", "Password is incorrect.");
            dialog.show();
            password.requestFocus();
            return false;
        }
        else
        {
            return true;
        }
    }

    AlertDialog showAlertDialog(String txtTitle, String message)
    {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        final TextView title = new TextView(this);
        title.setText(txtTitle);
        title.setTextSize(20);
        title.setTextColor(Color.WHITE);
        title.setGravity(Gravity.CENTER);
        builder.setCustomTitle(title);
        builder.setMessage(message);
        builder.setPositiveButton("OK", null);
        AlertDialog dialog = builder.show();
        TextView messageText = (TextView) dialog.findViewById(android.R.id.message);
        messageText.setGravity(Gravity.CENTER);
        return dialog;
    }

    AlertDialog showAlertDialogResetPassword(String txtTitle, String message)
    {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        final EditText emailAddress = new EditText(this);
        final TextView title = new TextView(this);
        emailAddress.setHint("Email Address");
        builder.setView(emailAddress);
        title.setText(txtTitle);
        title.setTextSize(20);
        title.setTextColor(Color.WHITE);
        title.setGravity(Gravity.CENTER);
        builder.setCustomTitle(title);
        builder.setMessage(message);
        builder.setPositiveButton("Cancel", new DialogInterface.OnClickListener()
        {
            public void onClick(DialogInterface dialog, int which)
            {
            }
        });
        builder.setNegativeButton("Reset", new DialogInterface.OnClickListener()
        {
            public void onClick(DialogInterface dialog, int which)
            {
                String _emailAddress = emailAddress.getText().toString();
                if (_emailAddress.matches(EMAIL_PATTERN) == false)
                {
                    AlertDialog dialogError = showAlertDialog("Request Error", "Invalid email address");
                    dialogError.show();
                    etEmail.requestFocus();
                }
            }
        });
        AlertDialog dialog = builder.show();
        TextView messageText = (TextView) dialog.findViewById(android.R.id.message);
        messageText.setGravity(Gravity.CENTER);
        return dialog;
    }
}