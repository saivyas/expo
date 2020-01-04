//  Copyright © 2019 650 Industries. All rights reserved.

#import <EXUpdates/EXUpdatesAppLoader+Private.h>

NS_ASSUME_NONNULL_BEGIN

@interface EXUpdatesAppLoaderEmbedded : EXUpdatesAppLoader

@property (nonatomic, strong, readonly) EXUpdatesUpdate * _Nullable embeddedManifest;

- (void)loadUpdateFromEmbeddedManifest;

@end

NS_ASSUME_NONNULL_END
