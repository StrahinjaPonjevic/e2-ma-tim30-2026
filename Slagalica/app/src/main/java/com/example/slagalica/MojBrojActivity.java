package com.example.slagalica;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

public class MojBrojActivity extends AppCompatActivity {

    private TextView tvExpressionValue;
    private TextView tvTargetNumber;
    private TextView tvCurrentResult;
    private Button btnClearExpression;
    private Button btnConfirmExpression;

    private Button[] numberButtons;
    private Button[] operatorButtons;
    private final List<ExpressionToken> expressionTokens = new ArrayList<>();
    private int stopStep = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_moj_broj);

        tvExpressionValue = findViewById(R.id.tvExpressionValue);
        tvTargetNumber = findViewById(R.id.tvTargetNumber);
        tvCurrentResult = findViewById(R.id.tvCurrentResult);
        btnClearExpression = findViewById(R.id.btnClearExpression);
        btnConfirmExpression = findViewById(R.id.btnConfirmExpression);

        numberButtons = new Button[] {
                findViewById(R.id.btnNumber1),
                findViewById(R.id.btnNumber2),
                findViewById(R.id.btnNumber3),
                findViewById(R.id.btnNumber4),
                findViewById(R.id.btnNumber5),
                findViewById(R.id.btnNumber6)
        };

        operatorButtons = new Button[] {
                findViewById(R.id.btnOpPlus),
                findViewById(R.id.btnOpMinus),
                findViewById(R.id.btnOpMultiply),
                findViewById(R.id.btnOpDivide),
                findViewById(R.id.btnOpLeftBracket),
                findViewById(R.id.btnOpRightBracket)
        };

        bindNumberButtons();
        bindOperatorButtons();
        bindActionButtons();
        setInputEnabled(false);
    }

    private void bindNumberButtons() {
        for (Button numberButton : numberButtons) {
            numberButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    appendToExpression(numberButton.getText().toString(), numberButton);
                    numberButton.setEnabled(false);
                }
            });
        }
    }

    private void bindOperatorButtons() {
        for (Button operatorButton : operatorButtons) {
            operatorButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    appendToExpression(operatorButton.getText().toString(), null);
                }
            });
        }
    }

    private void bindActionButtons() {
        btnClearExpression.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                removeLastToken();
            }
        });

        btnConfirmExpression.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                handleMainButtonClick();
            }
        });
    }

    private void handleMainButtonClick() {
        if (stopStep == 0) {
            tvTargetNumber.setText("Trazeni broj: 300");
            stopStep = 1;
            return;
        }

        if (stopStep == 1) {
            int[] sampleNumbers = {3, 7, 4, 9, 15, 100};
            for (int i = 0; i < numberButtons.length; i++) {
                numberButtons[i].setText(String.valueOf(sampleNumbers[i]));
            }
            setInputEnabled(true);
            btnConfirmExpression.setText("POTVRDI");
            stopStep = 2;
            return;
        }

        String expression = getExpressionText();
        if (expression.isEmpty()) {
            Toast.makeText(this, "Izraz je prazan", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "Izraz je poslat: " + expression, Toast.LENGTH_SHORT).show();
    }

    private void appendToExpression(String token, Button sourceButton) {
        expressionTokens.add(new ExpressionToken(token, sourceButton));
        refreshExpression();
    }

    private void removeLastToken() {
        if (expressionTokens.isEmpty()) {
            return;
        }

        ExpressionToken removedToken = expressionTokens.remove(expressionTokens.size() - 1);
        if (removedToken.sourceButton != null) {
            removedToken.sourceButton.setEnabled(true);
        }

        refreshExpression();
    }

    private void refreshExpression() {
        String expression = getExpressionText();
        tvExpressionValue.setText("Izraz: " + expression);
        tvCurrentResult.setText("= " + calculateSimpleResult(expression));
    }

    private String getExpressionText() {
        StringBuilder expression = new StringBuilder();
        for (ExpressionToken token : expressionTokens) {
            if (expression.length() > 0) {
                expression.append(" ");
            }
            expression.append(token.value);
        }
        return expression.toString();
    }

    private String calculateSimpleResult(String expression) {
        if (expression.isEmpty()) {
            return "---";
        }

        try {
            String[] parts = expression.split(" ");
            if (parts.length == 0) {
                return "---";
            }

            int result = Integer.parseInt(parts[0]);
            for (int i = 1; i < parts.length - 1; i += 2) {
                String operator = parts[i];
                int value = Integer.parseInt(parts[i + 1]);
                if (operator.equals("+")) {
                    result += value;
                } else if (operator.equals("-")) {
                    result -= value;
                } else if (operator.equals("*")) {
                    result *= value;
                } else if (operator.equals("/") && value != 0) {
                    result /= value;
                } else {
                    return "---";
                }
            }

            return String.valueOf(result);
        } catch (NumberFormatException exception) {
            return "---";
        }
    }

    private void setInputEnabled(boolean enabled) {
        for (Button numberButton : numberButtons) {
            numberButton.setEnabled(enabled);
        }

        for (Button operatorButton : operatorButtons) {
            operatorButton.setEnabled(enabled);
        }

        btnClearExpression.setEnabled(enabled);
    }

    private static class ExpressionToken {
        private final String value;
        private final Button sourceButton;

        private ExpressionToken(String value, Button sourceButton) {
            this.value = value;
            this.sourceButton = sourceButton;
        }
    }
}
