package expo.modules.updates;

import expo.modules.updates.db.entity.UpdateEntity;

import java.util.List;

public interface SelectionPolicy {
  public UpdateEntity selectUpdateToLaunch(List<UpdateEntity> updates);
}
