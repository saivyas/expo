package expo.modules.updates;

import expo.modules.updates.db.entity.UpdateEntity;

import java.util.List;

/**
 * Simple Update selection policy which chooses
 * the newest update (based on commit time) out
 * of all the possible stored updates.
 */
public class SelectionPolicyNewest implements SelectionPolicy {

  public UpdateEntity selectUpdateToLaunch(List<UpdateEntity> updates) {
    UpdateEntity updateToLaunch = null;
    for (UpdateEntity update : updates) {
      if (updateToLaunch == null || updateToLaunch.commitTime.before(update.commitTime)) {
        updateToLaunch = update;
      }
    }
    return updateToLaunch;
  }
}
