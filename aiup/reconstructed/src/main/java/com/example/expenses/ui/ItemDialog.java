package com.example.expenses.ui;

import com.example.expenses.domain.ClaimItem;
import com.example.expenses.domain.ExpenseCategory;
import com.example.expenses.domain.ItemData;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.TextField;
import java.util.function.Consumer;

/**
 * UC-002: dialog for adding or editing an expense item.
 * Business validation (BR-007, BR-008) happens in the service; this dialog collects input.
 */
public class ItemDialog extends Dialog {

    private final ComboBox<ExpenseCategory> category = new ComboBox<>("Category");
    private final TextField description = new TextField("Description");
    private final BigDecimalField amount = new BigDecimalField("Amount (EUR)");
    private final DatePicker expenseDate = new DatePicker("Expense date");
    private final Checkbox hasReceipt = new Checkbox("Receipt attached");

    /**
     * @param existing item to edit, or {@code null} to add a new one
     * @param onSave   called with the entered data; the dialog stays open so the
     *                 caller can keep it open when validation fails
     */
    public ItemDialog(ClaimItem existing, Consumer<ItemData> onSave) {
        setHeaderTitle(existing == null ? "Add expense item" : "Edit expense item");

        category.setItems(ExpenseCategory.values());
        category.setItemLabelGenerator(ExpenseCategory::label);

        if (existing != null) {
            category.setValue(existing.category());
            description.setValue(existing.description());
            amount.setValue(existing.amount());
            expenseDate.setValue(existing.expenseDate());
            hasReceipt.setValue(existing.hasReceipt());
        }

        FormLayout form = new FormLayout(category, description, amount, expenseDate, hasReceipt);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));
        add(form);

        Button save = new Button("Save", click -> onSave.accept(new ItemData(
                category.getValue(),
                description.getValue(),
                amount.getValue(),
                expenseDate.getValue(),
                Boolean.TRUE.equals(hasReceipt.getValue()))));
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button cancel = new Button("Cancel", click -> close());
        getFooter().add(cancel, save);
    }
}
