//  Copyright © 2019 650 Industries. All rights reserved.

#import <EXUpdates/EXUpdatesSelectionPolicyNewest.h>

NS_ASSUME_NONNULL_BEGIN

@implementation EXUpdatesSelectionPolicyNewest

- (EXUpdatesUpdate * _Nullable)launchableUpdateWithUpdates:(NSArray<EXUpdatesUpdate *>*)updates
{
  EXUpdatesUpdate *runnableUpdate;
  NSDate *runnableUpdateCommitTime;
  for (EXUpdatesUpdate *update in updates) {
    NSArray<NSString *>*compatibleBinaryVersions = [update.binaryVersions componentsSeparatedByString:@","];
    if (![compatibleBinaryVersions containsObject:[self binaryVersion]]) {
      continue;
    }
    NSDate *commitTime = update.commitTime;
    if (!runnableUpdateCommitTime || [runnableUpdateCommitTime compare:commitTime] == NSOrderedAscending) {
      runnableUpdate = update;
      runnableUpdateCommitTime = commitTime;
    }
  }
  return runnableUpdate;
}

- (NSArray<EXUpdatesUpdate *>*)updatesToDeleteWithLaunchedUpdate:(EXUpdatesUpdate *)launchedUpdate updates:(NSArray<EXUpdatesUpdate *>*)updates
{
  if (!launchedUpdate) {
    return @[];
  }

  NSMutableArray<EXUpdatesUpdate *>*updatesToDelete = [NSMutableArray new];
  for (EXUpdatesUpdate *update in updates) {
    if ([launchedUpdate.commitTime compare:update.commitTime] == NSOrderedDescending) {
      [updatesToDelete addObject:update];
    }
  }
  return updatesToDelete;
}

- (BOOL)shouldLoadNewUpdate:(EXUpdatesUpdate * _Nullable)newUpdate withLaunchedUpdate:(EXUpdatesUpdate * _Nullable)launchedUpdate
{
  if (!newUpdate) {
    return false;
  }
  if (!launchedUpdate) {
    return true;
  }
  return [launchedUpdate.commitTime compare:newUpdate.commitTime] == NSOrderedAscending;
}

- (NSString *)binaryVersion
{
  return [[[NSBundle mainBundle] infoDictionary] objectForKey:@"CFBundleShortVersionString"];
}

@end

NS_ASSUME_NONNULL_END
