//  Copyright © 2019 650 Industries. All rights reserved.

NS_ASSUME_NONNULL_BEGIN

typedef NS_ENUM(NSInteger, EXUpdatesCheckAutomaticallyConfig) {
  EXUpdatesCheckAutomaticallyConfigAlways = 0,
  EXUpdatesCheckAutomaticallyConfigWifiOnly = 1,
  EXUpdatesCheckAutomaticallyConfigNever = 2
};

@interface EXUpdatesConfig : NSObject

@property (nonatomic, readonly) NSURL *remoteUrl;
@property (nonatomic, readonly) NSString *releaseChannel;
@property (nonatomic, readonly) NSNumber *launchWaitMs;
@property (nonatomic, readonly) EXUpdatesCheckAutomaticallyConfig checkOnLaunch;

+ (instancetype)sharedInstance;

@end

NS_ASSUME_NONNULL_END
