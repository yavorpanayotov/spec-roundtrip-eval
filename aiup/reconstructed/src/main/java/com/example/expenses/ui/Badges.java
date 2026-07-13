package com.example.expenses.ui;

import com.example.expenses.domain.ClaimStatus;
import com.vaadin.flow.component.html.Span;

public final class Badges {

    private Badges() {
    }

    public static Span status(ClaimStatus status) {
        Span badge = new Span(status.label());
        badge.getElement().getThemeList().add("badge");
        switch (status) {
            case SUBMITTED -> badge.getElement().getThemeList().add("primary");
            case APPROVED, REIMBURSED -> badge.getElement().getThemeList().add("success");
            case REJECTED -> badge.getElement().getThemeList().add("error");
            case DRAFT -> badge.getElement().getThemeList().add("contrast");
        }
        return badge;
    }
}
