package life.genny.qwandaq.entity.search.clause;

/**
 * And
 */
public class And extends Clause implements ClauseArgument {

  public And() {
    super();
  }

  public And(ClauseArgument a, ClauseArgument b) {
    super(a, b);
  }

}
