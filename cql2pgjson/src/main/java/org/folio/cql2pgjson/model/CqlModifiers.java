package org.folio.cql2pgjson.model;

import java.util.List;

import org.folio.cql2pgjson.exception.CQLFeatureUnsupportedException;
import org.folio.cql2pgjson.exception.QueryValidationException;
import org.z3950.zing.cql.CQLTermNode;
import org.z3950.zing.cql.Modifier;
import org.z3950.zing.cql.ModifierSet;

public class CqlModifiers {
  public CqlSort cqlSort = CqlSort.ASCENDING;
  public CqlCase cqlCase = CqlCase.IGNORE_CASE;
  public CqlAccents cqlAccents = CqlAccents.IGNORE_ACCENTS;
  public CqlTermFormat cqlTermFormat = CqlTermFormat.STRING;
  public CqlMasking cqlMasking = CqlMasking.MASKED;

  public CqlModifiers(CQLTermNode node) throws CQLFeatureUnsupportedException {
    readModifiers(node.getRelation().getModifiers());
  }

  public CqlModifiers(ModifierSet modifierSet) throws CQLFeatureUnsupportedException {
    readModifiers(modifierSet.getModifiers());
  }

  /**
   * Read the modifiers and write the last for each enum into the enum variable.
   * Default is ascending, ignoreCase, ignoreAccents and masked.
   *
   * @param modifiers where to read from
   * @throws QueryValidationException
   */
  @SuppressWarnings("squid:MethodCyclomaticComplexity")
  public final void readModifiers(List<Modifier> modifiers) throws CQLFeatureUnsupportedException {
    for (Modifier m : modifiers) {
      switch (m.getType()) {
      case "ignorecase":
        cqlCase = CqlCase.IGNORE_CASE;
        break;
      case "respectcase":
        cqlCase = CqlCase.RESPECT_CASE;
        break;
      case "ignoreaccents":
        cqlAccents = CqlAccents.IGNORE_ACCENTS;
        break;
      case "respectaccents":
        cqlAccents = CqlAccents.RESPECT_ACCENTS;
        break;
      case "string":
        cqlTermFormat = CqlTermFormat.STRING;
        break;
      case "number":
        cqlTermFormat = CqlTermFormat.NUMBER;
        break;
      case "sort.ascending":
        cqlSort = CqlSort.ASCENDING;
        break;
      case "sort.descending":
        cqlSort = CqlSort.DESCENDING;
        break;
      case "masked":
        cqlMasking = CqlMasking.MASKED;
        break;
//      case "unmasked":
//        cqlMasking = CqlMasking.UNMASKED;
//        break;
//      case "substring":
//        cqlMasking = CqlMasking.SUBSTRING;
//        break;
//      case "regexp":
//        cqlMasking = CqlMasking.REGEXP;
//        break;
      default:
        throw new CQLFeatureUnsupportedException("CQL: Unsupported modifier " + m.getType());
      }
    }
  }
}
