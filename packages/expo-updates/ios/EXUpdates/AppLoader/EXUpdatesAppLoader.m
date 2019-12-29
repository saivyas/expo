//  Copyright © 2019 650 Industries. All rights reserved.

#import <EXUpdates/EXUpdatesAppController.h>
#import <EXUpdates/EXUpdatesAppLoader+Private.h>
#import <EXUpdates/EXUpdatesDatabase.h>
#import <EXUpdates/EXUpdatesFileDownloader.h>
#import <EXUpdates/EXUpdatesUtils.h>

NS_ASSUME_NONNULL_BEGIN

@interface EXUpdatesAppLoader ()

@property (nonatomic, strong) NSMutableArray<EXUpdatesAsset *>* assetQueue;
@property (nonatomic, strong) NSMutableArray<EXUpdatesAsset *>* erroredAssets;
@property (nonatomic, strong) NSMutableArray<EXUpdatesAsset *>* finishedAssets;

@end

static NSString * const kEXUpdatesAppLoaderErrorDomain = @"EXUpdatesAppLoader";

@implementation EXUpdatesAppLoader

/**
 * we expect the server to respond with a JSON object with the following fields:
 * id (UUID string)
 * commitTime (timestamp number)
 * binaryVersions (comma separated list - string)
 * bundleUrl (string)
 * metadata (arbitrary object)
 * assets (array of asset objects with `url` and `type` keys)
 */

- (instancetype)init
{
  if (self = [super init]) {
    _assetQueue = [NSMutableArray new];
    _erroredAssets = [NSMutableArray new];
    _finishedAssets = [NSMutableArray new];
  }
  return self;
}

# pragma mark - subclass methods

- (void)loadUpdateFromUrl:(NSURL *)url
{
  @throw [NSException exceptionWithName:NSInternalInconsistencyException reason:@"Should not call EXUpdatesAppLoader#loadUpdate -- use a subclass instead" userInfo:nil];
}

- (void)downloadAsset:(EXUpdatesAsset *)asset
{
  @throw [NSException exceptionWithName:NSInternalInconsistencyException reason:@"Should not call EXUpdatesAppLoader#loadUpdate -- use a subclass instead" userInfo:nil];
}

# pragma mark - loading and database logic

- (void)startLoadingFromManifest
{
  [self _writeManifestToDatabase];

  if (_delegate) {
    [_delegate appLoader:self didStartLoadingUpdateWithMetadata:_manifest.metadata];
  }

  [self _addAllAssetTasksToQueues];

  for (EXUpdatesAsset *asset in _assetQueue) {
    // TODO: check database to make sure we don't already have this downloaded
    [self downloadAsset:asset];
  }
}

- (void)handleAssetDownloadWithError:(NSError *)error asset:(EXUpdatesAsset *)asset
{
  // TODO: retry. for now log an error
  NSLog(@"error downloading file: %@: %@", [asset.url absoluteString], [error localizedDescription]);
  [_assetQueue removeObject:asset];
  [_erroredAssets addObject:asset];
  if (![self->_assetQueue count]) {
    [self _finish];
  }
}

- (void)handleAssetDownloadWithData:(NSData *)data response:(NSURLResponse * _Nullable)response asset:(EXUpdatesAsset *)asset
{
  [self->_assetQueue removeObject:asset];

  asset.data = data;
  asset.response = response;
  asset.downloadTime = [NSDate date];
  [_finishedAssets addObject:asset];

  if (![self->_assetQueue count]) {
    [self _finish];
  }
}

# pragma mark - internal

- (void)_finish
{
  [[EXUpdatesAppController sharedInstance].database addAssets:_finishedAssets toUpdateWithId:_manifest.updateId];
  [self _unlockDatabase];
  if (_delegate) {
    if ([_erroredAssets count]) {
      [_delegate appLoader:self didFailWithError:[NSError errorWithDomain:kEXUpdatesAppLoaderErrorDomain
                                                                     code:-1
                                                                 userInfo:@{
                                                                            NSLocalizedDescriptionKey: @"Failed to download all assets"
                                                                            }]];
    } else {
      [_delegate appLoader:self didFinishLoadingUpdateWithId:_manifest.updateId];
    }
  }
}

- (void)_writeManifestToDatabase
{
  [self _lockDatabase];

  EXUpdatesDatabase *database = [EXUpdatesAppController sharedInstance].database;
  [database addUpdateWithManifest:_manifest];
}

- (void)_addAllAssetTasksToQueues
{
  _assetQueue = [_manifest.assets copy];
}

# pragma mark - helpers

- (void)_lockDatabase
{
  [[EXUpdatesAppController sharedInstance].database.lock lock];
}

- (void)_unlockDatabase
{
  [[EXUpdatesAppController sharedInstance].database.lock unlock];
}

@end

NS_ASSUME_NONNULL_END
