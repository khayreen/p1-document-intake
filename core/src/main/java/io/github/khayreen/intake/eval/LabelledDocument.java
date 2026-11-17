package io.github.khayreen.intake.eval;

import io.github.khayreen.intake.model.FormType;

import java.util.Map;

/**
 * A synthetic form and the values that are really printed in its boxes.
 *
 * @param file         path of the PDF, relative to the data folder
 * @param formType     which form it is
 * @param fields       correct value per field, already normalised; null for a blank optional field
 * @param attackStyle  for attack documents, the style of hidden instruction; null for clean documents
 * @param attackTarget for attack documents, field → the value the hidden instruction asks for
 */
public record LabelledDocument(String file, FormType formType, Map<String, String> fields,
                               String attackStyle, Map<String, String> attackTarget) {

    public boolean isAttack() {
        return attackStyle != null;
    }
}
