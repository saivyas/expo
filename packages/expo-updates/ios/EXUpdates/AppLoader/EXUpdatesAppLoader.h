//  Copyright © 2019 650 Industries. All rights reserved.

#import <EXUpdates/EXUpdatesUpdate.h>

NS_ASSUME_NONNULL_BEGIN

@class EXUpdatesAppLoader;

@protocol EXUpdatesAppLoaderDelegate <NSObject>

- (BOOL)appLoader:(EXUpdatesAppLoader *)appLoader shouldStartLoadingUpdate:(EXUpdatesUpdate *)update;
- (void)appLoader:(EXUpdatesAppLoader *)appLoader didFinishLoadingUpdate:(EXUpdatesUpdate * _Nullable)update;
- (void)appLoader:(EXUpdatesAppLoader *)appLoader didFailWithError:(NSError *)error;

@end

@interface EXUpdatesAppLoader : NSObject

@property (nonatomic, weak) id<EXUpdatesAppLoaderDelegate> delegate;

- (void)loadUpdateFromUrl:(NSURL *)url;

@end

NS_ASSUME_NONNULL_END
