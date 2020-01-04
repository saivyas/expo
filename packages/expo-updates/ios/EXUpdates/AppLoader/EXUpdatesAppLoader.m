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
@property (nonatomic, strong) NSMutableArray<EXUpdatesAsset *>* existingAssets;

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

- (void)startLoadingFromManifest:(EXUpdatesUpdate *)updateManifest
{
  if (_delegate) {
    BOOL shouldContinue = [_delegate appLoader:self shouldStartLoadingUpdate:_updateManifest];
    if (!shouldContinue) {
      [_delegate appLoader:self didFinishLoadingUpdate:nil];
      return;
    }
  }

  [self _lockDatabase];

  EXUpdatesDatabase *database = [EXUpdatesAppController sharedInstance].database;
  EXUpdatesUpdate *existingUpdate = [database updateWithId:updateManifest.updateId];
  if (existingUpdate && existingUpdate.status == EXUpdatesUpdateStatusReady) {
    [self _unlockDatabase];
    [_delegate appLoader:self didFinishLoadingUpdate:updateManifest];
  } else {
    if (existingUpdate) {
      // we've already partially downloaded the update, so we should use the existing entity.
      // however, it's not ready, so we should try to download all the assets again.
      _updateManifest = existingUpdate;
    } else {
      // no update already exists with this ID, so we need to insert it and download everything.
      _updateManifest = updateManifest;
      [database addUpdate:_updateManifest];
    }

    _assetQueue = [_updateManifest.assets copy];

    for (EXUpdatesAsset *asset in _assetQueue) {
      [self downloadAsset:asset];
    }
  }
}

- (void)handleAssetDownloadAlreadyExists:(EXUpdatesAsset *)asset
{
  [self->_assetQueue removeObject:asset];
  [self->_existingAssets addObject:asset];
  if (![self->_assetQueue count]) {
    [self _finish];
  }
}

- (void)handleAssetDownloadWithError:(NSError *)error asset:(EXUpdatesAsset *)asset
{
  // TODO: retry. for now log an error
  NSLog(@"error downloading file: %@: %@", [asset.url absoluteString], [error localizedDescription]);
  [self->_assetQueue removeObject:asset];
  [self->_erroredAssets addObject:asset];
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
  [self->_finishedAssets addObject:asset];

  if (![self->_assetQueue count]) {
    [self _finish];
  }
}

# pragma mark - internal

- (void)_finish
{
  EXUpdatesDatabase *database = [EXUpdatesAppController sharedInstance].database;
  for (EXUpdatesAsset *existingAsset in _existingAssets) {
    BOOL existingAssetFound = [database addExistingAsset:existingAsset toUpdateWithId:_updateManifest.updateId];
    if (!existingAssetFound) {
      // the database and filesystem have gotten out of sync
      // do our best to create a new entry for this file even though it already existed on disk
      existingAsset.downloadTime = [NSDate date];
      existingAsset.data = [NSData dataWithContentsOfURL:[[EXUpdatesAppController sharedInstance].updatesDirectory URLByAppendingPathComponent:existingAsset.filename]];
      [_finishedAssets addObject:existingAsset];
    }
  }
  [database addNewAssets:_finishedAssets toUpdateWithId:_updateManifest.updateId];

  if (_delegate) {
    if ([_erroredAssets count]) {
      [self _unlockDatabase];
      [_delegate appLoader:self didFailWithError:[NSError errorWithDomain:kEXUpdatesAppLoaderErrorDomain
                                                                     code:-1
                                                                 userInfo:@{
                                                                            NSLocalizedDescriptionKey: @"Failed to download all assets"
                                                                            }]];
    } else {
      [database markUpdateReadyWithId:_updateManifest.updateId];
      [self _unlockDatabase];
      [_delegate appLoader:self didFinishLoadingUpdate:_updateManifest];
    }
  }
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
