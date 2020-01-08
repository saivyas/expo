//  Copyright © 2019 650 Industries. All rights reserved.

#import <EXUpdates/EXUpdatesAppController.h>
#import <EXUpdates/EXUpdatesDatabase.h>
#import <EXUpdates/EXUpdatesUpdate.h>
#import <EXUpdates/EXUpdatesUtils.h>
#import <React/RCTConvert.h>

NS_ASSUME_NONNULL_BEGIN

static NSString * const kEXUpdatesEmbeddedBundleFilename = @"shell-app.bundle";

@interface EXUpdatesUpdate ()

@property (nonatomic, strong, readwrite) NSUUID *updateId;
@property (nonatomic, strong, readwrite) NSDate *commitTime;
@property (nonatomic, strong, readwrite) NSString *binaryVersions;
@property (nonatomic, strong, readwrite) NSDictionary * _Nullable metadata;
@property (nonatomic, assign, readwrite) EXUpdatesUpdateStatus status;
@property (nonatomic, assign, readwrite) BOOL keep;
@property (nonatomic, strong, readwrite) NSURL *bundleUrl;
@property (nonatomic, strong, readwrite) NSArray<EXUpdatesAsset *>*assets;

@property (nonatomic, strong, readwrite) NSDictionary *rawManifest;

@end

@implementation EXUpdatesUpdate

- (instancetype)_initWithRawManifest:(NSDictionary *)manifest
{
  if (self = [super init]) {
    _rawManifest = manifest;
  }
  return self;
}

+ (instancetype)updateWithId:(NSUUID *)updateId
    commitTime:(NSDate *)commitTime
binaryVersions:(NSString *)binaryVersions
      metadata:(NSDictionary * _Nullable)metadata
        status:(EXUpdatesUpdateStatus)status
          keep:(BOOL)keep
{
  // for now, we store the entire managed manifest in the metadata field
  EXUpdatesUpdate *update = [[self alloc] _initWithRawManifest:metadata];
  update.updateId = updateId;
  update.commitTime = commitTime;
  update.binaryVersions = binaryVersions;
  update.metadata = metadata;
  update.status = status;
  update.keep = keep;
  return update;
}

+ (instancetype)updateWithBareManifest:(NSDictionary *)bareManifest
{
  EXUpdatesUpdate *update = [[self alloc] _initWithRawManifest:bareManifest];

  id updateId = bareManifest[@"id"];
  id commitTime = bareManifest[@"commitTime"];
  id binaryVersions = bareManifest[@"binaryVersions"];
  id metadata = bareManifest[@"metadata"];
  id bundleUrlString = bareManifest[@"bundleUrl"];
  id assets = bareManifest[@"assets"];

  NSAssert([updateId isKindOfClass:[NSString class]], @"update ID should be a string");
  NSAssert([commitTime isKindOfClass:[NSNumber class]], @"commitTime should be a number");
  NSAssert([binaryVersions isKindOfClass:[NSString class]], @"binaryVersions should be a string");
  NSAssert(!metadata || [metadata isKindOfClass:[NSDictionary class]], @"metadata should be null or an object");
  NSAssert([bundleUrlString isKindOfClass:[NSString class]], @"bundleUrl should be a string");
  NSAssert(assets && [assets isKindOfClass:[NSArray class]], @"assets should be a nonnull array");

  NSUUID *uuid = [[NSUUID alloc] initWithUUIDString:(NSString *)updateId];
  NSAssert(uuid, @"update ID should be a valid UUID");
  NSURL *bundleUrl = [NSURL URLWithString:bundleUrlString];
  NSAssert(bundleUrl, @"bundleUrl should be a valid URL");

  NSMutableArray<EXUpdatesAsset *>*processedAssets = [NSMutableArray new];
  EXUpdatesAsset *jsBundleAsset = [[EXUpdatesAsset alloc] initWithUrl:bundleUrl type:@"js"];
  jsBundleAsset.isLaunchAsset = YES;
  jsBundleAsset.nsBundleFilename = kEXUpdatesEmbeddedBundleFilename;
  jsBundleAsset.filename = [EXUpdatesUtils sha1WithData:[[bundleUrl absoluteString] dataUsingEncoding:NSUTF8StringEncoding]];
  [processedAssets addObject:jsBundleAsset];

  for (NSDictionary *assetDict in (NSArray *)assets) {
    NSAssert([assetDict isKindOfClass:[NSDictionary class]], @"assets must be objects");
    id urlString = assetDict[@"url"];
    id type = assetDict[@"type"];
    id metadata = assetDict[@"metadata"];
    id nsBundleFilename = assetDict[@"nsBundleFilename"];
    NSAssert(urlString && [urlString isKindOfClass:[NSString class]], @"asset url should be a nonnull string");
    NSAssert(type && [type isKindOfClass:[NSString class]], @"asset type should be a nonnull string");
    NSURL *url = [NSURL URLWithString:(NSString *)urlString];
    NSAssert(url, @"asset url should be a valid URL");

    EXUpdatesAsset *asset = [[EXUpdatesAsset alloc] initWithUrl:url type:(NSString *)type];

    if (metadata) {
      NSAssert([metadata isKindOfClass:[NSDictionary class]], @"asset metadata should be an object");
      asset.metadata = (NSDictionary *)metadata;
    }

    if (nsBundleFilename) {
      NSAssert([nsBundleFilename isKindOfClass:[NSString class]], @"asset localPath should be a string");
      asset.nsBundleFilename = (NSString *)nsBundleFilename;
    }

    asset.filename = [EXUpdatesUtils sha1WithData:[(NSString *)urlString dataUsingEncoding:NSUTF8StringEncoding]];

    [processedAssets addObject:asset];
  }

  update.updateId = uuid;
  update.commitTime = [NSDate dateWithTimeIntervalSince1970:[(NSNumber *)commitTime doubleValue] / 1000];
  update.binaryVersions = (NSString *)binaryVersions;
  if (metadata) {
    update.metadata = (NSDictionary *)metadata;
  }
  update.status = EXUpdatesUpdateStatusPending;
  update.keep = YES;
  update.bundleUrl = bundleUrl;
  update.assets = processedAssets;

  return update;
}

+ (instancetype)updateWithManagedManifest:(NSDictionary *)managedManifest
{
  EXUpdatesUpdate *update = [[self alloc] _initWithRawManifest:managedManifest];

  id updateId = managedManifest[@"releaseId"];
  id commitTime = managedManifest[@"commitTime"];
  id bundleUrlString = managedManifest[@"bundleUrl"];
  id assets = managedManifest[@"bundledAssets"];

  id sdkVersion = managedManifest[@"sdkVersion"];
  id binaryVersions = managedManifest[@"binaryVersions"];
  if (binaryVersions && [binaryVersions isKindOfClass:[NSDictionary class]]) {
    id binaryVersionsIos = ((NSDictionary *)binaryVersions)[@"ios"];
    NSAssert([binaryVersionsIos isKindOfClass:[NSString class]], @"binaryVersions['ios'] should be a string");
    update.binaryVersions = (NSString *)binaryVersionsIos;
  } else if (binaryVersions && [binaryVersions isKindOfClass:[NSString class]]) {
    update.binaryVersions = (NSString *)binaryVersions;
  } else {
    NSAssert([sdkVersion isKindOfClass:[NSString class]], @"sdkVersion should be a string");
    update.binaryVersions = (NSString *)sdkVersion;
  }

  NSAssert([updateId isKindOfClass:[NSString class]], @"update ID should be a string");
  NSAssert([commitTime isKindOfClass:[NSString class]], @"commitTime should be a string");
  NSAssert([bundleUrlString isKindOfClass:[NSString class]], @"bundleUrl should be a string");
  NSAssert(assets && [assets isKindOfClass:[NSArray class]], @"assets should be a nonnull array");

  NSUUID *uuid = [[NSUUID alloc] initWithUUIDString:(NSString *)updateId];
  NSAssert(uuid, @"update ID should be a valid UUID");
  NSURL *bundleUrl = [NSURL URLWithString:bundleUrlString];
  NSAssert(bundleUrl, @"bundleUrl should be a valid URL");

  NSMutableArray<EXUpdatesAsset *>*processedAssets = [NSMutableArray new];
  EXUpdatesAsset *jsBundleAsset = [[EXUpdatesAsset alloc] initWithUrl:bundleUrl type:@"js"];
  jsBundleAsset.isLaunchAsset = YES;
  jsBundleAsset.nsBundleFilename = kEXUpdatesEmbeddedBundleFilename;
  jsBundleAsset.filename = [EXUpdatesUtils sha1WithData:[[bundleUrl absoluteString] dataUsingEncoding:NSUTF8StringEncoding]];
  [processedAssets addObject:jsBundleAsset];

  for (NSString *bundledAsset in (NSArray *)assets) {
    NSAssert([bundledAsset isKindOfClass:[NSString class]], @"bundledAssets must be an array of strings");

    NSRange extensionStartRange = [bundledAsset rangeOfString:@"." options:NSBackwardsSearch];
    int prefixLength = [@"asset_" length];
    NSString *hash;
    NSString *type;
    if (extensionStartRange.location == NSNotFound) {
      hash = [bundledAsset substringFromIndex:prefixLength];
      type = @"";
    } else {
      NSRange hashRange = NSMakeRange(prefixLength, extensionStartRange.location);
      hash = [bundledAsset substringWithRange:hashRange];
      type = [bundledAsset substringFromIndex:extensionStartRange.location + 1];
    }

    NSURL *url = [NSURL URLWithString:hash relativeToURL:[[self class] bundledAssetBaseUrl]];

    EXUpdatesAsset *asset = [[EXUpdatesAsset alloc] initWithUrl:url type:(NSString *)type];
    asset.nsBundleFilename = (NSString *)bundledAsset;

    asset.filename = [EXUpdatesUtils sha1WithData:[[url absoluteString] dataUsingEncoding:NSUTF8StringEncoding]];

    [processedAssets addObject:asset];
  }

  update.updateId = uuid;
  update.commitTime = [RCTConvert NSDate:commitTime];
  update.metadata = managedManifest;
  update.status = EXUpdatesUpdateStatusPending;
  update.keep = YES;
  update.bundleUrl = bundleUrl;
  update.assets = processedAssets;

  return update;
}

+ (NSURL *)bundledAssetBaseUrl
{
  return [NSURL URLWithString:@"https://d1wp6m56sqw74a.cloudfront.net/~assets/"];
}

- (NSURL *)bundleUrl
{
  if (!_bundleUrl) {
    EXUpdatesDatabase *db = [EXUpdatesAppController sharedInstance].database;
    _bundleUrl = [db launchAssetWithUpdateId:_updateId].url;
  }
  return _bundleUrl;
}

- (NSArray<EXUpdatesAsset *>*)assets
{
  if (!_assets) {
    EXUpdatesDatabase *db = [EXUpdatesAppController sharedInstance].database;
    _assets = [db assetsWithUpdateId:_updateId];
  }
  return _assets;
}

@end

NS_ASSUME_NONNULL_END
