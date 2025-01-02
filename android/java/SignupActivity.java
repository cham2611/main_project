package com.cookandroid.ex2_1;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SignupActivity extends AppCompatActivity {
    private EditText nameInput;
    private EditText emailInput;
    private EditText passwordInput;
    private EditText confirmPasswordInput;
    private Button signupButton;
    private View rootView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        // 뷰 초기화
        initializeViews();

        // 회원가입 버튼 클릭 리스너 설정
        setupSignupButton();
    }

    private void initializeViews() {
        nameInput = findViewById(R.id.nameInput);
        emailInput = findViewById(R.id.emailInput);
        passwordInput = findViewById(R.id.passwordInput);
        confirmPasswordInput = findViewById(R.id.confirmPasswordInput);
        signupButton = findViewById(R.id.signupButton);
        rootView = findViewById(android.R.id.content);
    }

    private void setupSignupButton() {
        signupButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (validateInputs()) {
                    performSignup();
                }
            }
        });

        // 입력 영역은 클릭 이벤트가 전파되지 않도록 설정
        nameInput.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 클릭 이벤트 소비
                v.performClick();
            }
        });

        emailInput.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 클릭 이벤트 소비
                v.performClick();
            }
        });

        passwordInput.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 클릭 이벤트 소비
                v.performClick();
            }
        });

        confirmPasswordInput.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 클릭 이벤트 소비
                v.performClick();
            }
        });
    }

    private boolean validateInputs() {
        String name = nameInput.getText().toString().trim();
        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();
        String confirmPassword = confirmPasswordInput.getText().toString().trim();

        // 빈 필드 체크
        if (name.isEmpty() || email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
            showToast("모든 항목을 입력해주세요");
            return false;
        }

        // 이름 길이 체크
        if (name.length() < 2) {
            showToast("이름은 2자 이상이어야 합니다");
            return false;
        }

        // 비밀번호 길이 체크
        if (password.length() < 6) {
            showToast("비밀번호는 6자 이상이어야 합니다");
            return false;
        }

        // 비밀번호 일치 체크
        if (!password.equals(confirmPassword)) {
            showToast("비밀번호가 일치하지 않습니다");
            return false;
        }

        // 이메일 형식 체크
        if (!isValidEmail(email)) {
            showToast("올바른 이메일 형식이 아닙니다");
            return false;
        }

        return true;
    }

    private boolean isValidEmail(String email) {
        String emailPattern = "[a-zA-Z0-9._-]+@[a-z]+\\.+[a-z]+";
        return email.matches(emailPattern);
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void performSignup() {
        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();
        String name = nameInput.getText().toString().trim();

        UserRequest request = new UserRequest(email, password, name);
        ApiService service = RetrofitClient.getInstance().create(ApiService.class);

        Call<UserResponse> call = service.signup(request);
        call.enqueue(new Callback<UserResponse>() {
            @Override
            public void onResponse(Call<UserResponse> call, Response<UserResponse> response) {
                if (response.isSuccessful()) {
                    showToast("회원가입 성공");
                    finish();
                } else {
                    showToast("회원가입 실패: " + response.message());
                }
            }

            @Override
            public void onFailure(Call<UserResponse> call, Throwable t) {
                showToast("네트워크 오류: " + t.getMessage());
            }
        });
    }
}
