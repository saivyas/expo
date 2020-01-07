//  Copyright © 2019 650 Industries. All rights reserved.

#import <EXUpdates/EXUpdatesUpdate.h>
#import <EXUpdates/EXUpdatesSelectionPolicy.h>

NS_ASSUME_NONNULL_BEGIN

@interface EXUpdatesAppLauncher : NSObject

@property (nonatomic, strong, readonly) EXUpdatesUpdate * _Nullable launchedUpdate;

- (EXUpdatesUpdate * _Nullable)launchableUpdateWithSelectionPolicy:(EXUpdatesSelectionPolicy *)selectionPolicy;
- (EXUpdatesUpdate * _Nullable)launchUpdateWithSelectionPolicy:(EXUpdatesSelectionPolicy *)selectionPolicy;
- (NSUUID * _Nullable)launchedUpdateId;

@end

NS_ASSUME_NONNULL_END
