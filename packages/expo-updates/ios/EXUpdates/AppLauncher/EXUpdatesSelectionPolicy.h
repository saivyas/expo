//  Copyright © 2019 650 Industries. All rights reserved.

#import <EXUpdates/EXUpdatesUpdate.h>

NS_ASSUME_NONNULL_BEGIN

@interface EXUpdatesSelectionPolicy : NSObject

- (EXUpdatesUpdate * _Nullable)launchableUpdateWithUpdates:(NSArray<EXUpdatesUpdate *>*)updates;
- (NSArray<EXUpdatesUpdate *>*)updatesToDeleteWithLaunchedUpdate:(EXUpdatesUpdate *)launchedUpdate updates:(NSArray<EXUpdatesUpdate *>*)updates;
- (BOOL)shouldLoadNewUpdate:(EXUpdatesUpdate * _Nullable)newUpdate withLaunchedUpdate:(EXUpdatesUpdate * _Nullable)launchedUpdate;

@end

NS_ASSUME_NONNULL_END
