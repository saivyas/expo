//  Copyright © 2019 650 Industries. All rights reserved.

#import <EXUpdates/EXUpdatesAppController.h>
#import <EXUpdates/EXUpdatesAppLauncher.h>
#import <EXUpdates/EXUpdatesDatabase.h>

NS_ASSUME_NONNULL_BEGIN

@interface EXUpdatesAppLauncher ()

@property (nonatomic, strong, readwrite) EXUpdatesUpdate * _Nullable launchedUpdate;
@property (nonatomic, strong, readwrite) EXUpdatesUpdate * _Nullable launchableUpdate;

@end

static NSString * const kEXUpdatesAppLauncherErrorDomain = @"AppLauncher";

@implementation EXUpdatesAppLauncher

- (EXUpdatesUpdate * _Nullable)launchableUpdateWithSelectionPolicy:(EXUpdatesSelectionPolicy *)selectionPolicy
{
  if (!_launchableUpdate) {
    EXUpdatesDatabase *database = [EXUpdatesAppController sharedInstance].database;
    NSArray<EXUpdatesUpdate *>* launchableUpdates = [database launchableUpdates];
    _launchableUpdate = [selectionPolicy launchableUpdateFromUpdates:launchableUpdates];
  }
  return _launchableUpdate;
}

- (EXUpdatesUpdate * _Nullable)launchUpdateWithSelectionPolicy:(EXUpdatesSelectionPolicy *)selectionPolicy
{
  if (!_launchedUpdate) {
    _launchedUpdate = [self launchableUpdateWithSelectionPolicy:selectionPolicy];
  }
  
  // askdjfa;sdfj;asdjf;alsdjfsad ensure assets exist!!!!!
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
