//  Copyright © 2019 650 Industries. All rights reserved.

#import <EXUpdates/EXUpdatesAppController.h>
#import <EXUpdates/EXUpdatesAppLauncher.h>
#import <EXUpdates/EXUpdatesDatabase.h>

NS_ASSUME_NONNULL_BEGIN

@interface EXUpdatesAppLauncher ()

@property (nonatomic, strong, readwrite) EXUpdatesUpdate * _Nullable launchedUpdate;

@end

static NSString * const kEXUpdatesAppLauncherErrorDomain = @"AppLauncher";

@implementation EXUpdatesAppLauncher

- (EXUpdatesUpdate *)launchUpdateWithSelectionPolicy:(EXUpdatesSelectionPolicy *)selectionPolicy
{
  if (!_launchedUpdate) {
    EXUpdatesDatabase *database = [EXUpdatesAppController sharedInstance].database;
    NSArray<EXUpdatesUpdate *>* launchableUpdates = [database launchableUpdates];
    _launchedUpdate = [selectionPolicy launchableUpdateFromUpdates:launchableUpdates];
    if (!_launchedUpdate) {
      [[EXUpdatesAppController sharedInstance] handleErrorWithDomain:kEXUpdatesAppLauncherErrorDomain description:@"No runnable update found" info:nil isFatal:YES];
    }
  }
  return _launchedUpdate;
}

// TODO: get rid of this
- (NSUUID * _Nullable)launchedUpdateId
{
  if (!_launchedUpdate) {
    return nil;
  }
  return _launchedUpdate.updateId;
}

@end

NS_ASSUME_NONNULL_END
