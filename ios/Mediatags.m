#import "Mediatags.h"
#import "ArtworkImageData.h"
#import <AVFoundation/AVFoundation.h>
#import <React/RCTUIManager.h>
#import <TagLibIOS/TLAudio.h>

@implementation Mediatags

RCT_EXPORT_MODULE()

RCT_REMAP_METHOD(getMetaData, filePath:(NSString *)filePath resolver:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject)
{
    NSString *artist = @"";
    NSString *title = @"";
    NSString *album = @"";
    TLAudio *tlAsset = [[TLAudio alloc] initWithFileAtPath:filePath];
    
    if (tlAsset.artist && tlAsset.title && tlAsset.album) {
        artist = tlAsset.artist;
        title = tlAsset.title;
        album = tlAsset.album;
    } else {
        NSURL *fileURL = [NSURL fileURLWithPath:filePath];
        AVAsset *asset = [AVURLAsset URLAssetWithURL:fileURL options:nil];
        
        for (NSString *format in [asset availableMetadataFormats]) {
            for (AVMetadataItem *item in [asset metadataForFormat:format]) {
                if ([[item commonKey] isEqualToString:@"artist"]) {
                    artist = (NSString *)[item value];
                }
                if ([[item commonKey] isEqualToString:@"title"]) {
                    title = (NSString *)[item value];
                }
                if ([[item commonKey] isEqualToString:@"album"]) {
                    album = (NSString *)[item value];
                }
            }
        }
    }

    // Album-artist has no AVFoundation commonKey (unlike artist/title/album), so
    // it must be read from the format-specific tag: ID3 `TPE2` (band/orchestra)
    // or iTunes/MP4 `aART`. Matched by the metadata item identifier so both
    // container formats are covered. Kept separate from the artist/title/album
    // path above so it works regardless of which of those branches ran.
    NSString *albumArtist = @"";
    {
        NSURL *aaURL = [NSURL fileURLWithPath:filePath];
        AVAsset *aaAsset = [AVURLAsset URLAssetWithURL:aaURL options:nil];
        for (NSString *format in [aaAsset availableMetadataFormats]) {
            for (AVMetadataItem *item in [aaAsset metadataForFormat:format]) {
                NSString *idStr = [item identifier] ?: @"";
                NSString *keyStr = [[item key] isKindOfClass:[NSString class]] ? (NSString *)[item key] : @"";
                BOOL isAlbumArtist =
                    [idStr containsString:@"aART"] ||
                    [idStr containsString:@"TPE2"] ||
                    [idStr containsString:@"AlbumArtist"] ||
                    [keyStr isEqualToString:@"aART"] ||
                    [keyStr isEqualToString:@"TPE2"];
                if (isAlbumArtist && [[item value] isKindOfClass:[NSString class]]) {
                    albumArtist = (NSString *)[item value];
                }
            }
        }
    }

    NSDictionary *dict = @{ @"artist": artist, @"title": title, @"album": album, @"albumArtist": albumArtist };
    resolve(dict);
}

RCT_EXPORT_METHOD(getArtwork:(NSString *)filePath result:(RCTResponseSenderBlock)callback)
{
    NSData *songArtwork = [ArtworkImageData getData:filePath];
    NSString *base64 = @"";
    
    if (songArtwork != nil) {
        base64 = [songArtwork base64EncodedStringWithOptions:NSDataBase64EncodingEndLineWithLineFeed];
    }
    
    callback(@[base64]);
}

RCT_REMAP_METHOD(saveMetadata, filePath:(NSString *)filePath params:(NSDictionary *)params resolver:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject)
{
    NSString *artist = @"";
    NSString *title = @"";
    NSString *album = @"";
    TLAudio *tlAsset = [[TLAudio alloc] initWithFileAtPath:filePath];
    
    if ([params valueForKey:@"artist"] != nil) {
        [tlAsset setArtist:params[@"artist"]];
        artist = params[@"artist"];
    }
    if ([params valueForKey:@"title"] != nil) {
        [tlAsset setTitle:params[@"title"]];
        title = params[@"title"];
    }
    if ([params valueForKey:@"album"] != nil) {
        [tlAsset setAlbum:params[@"album"]];
        album = params[@"album"];
    }
    if ([params valueForKey:@"frontCoverPicture"] != nil) {
        NSError* error = nil;
        NSData *imageData = [NSData dataWithContentsOfFile:params[@"frontCoverPicture"] options: 0 error: &error];
        if (imageData == nil) {
            NSLog(@"Failed to read file, error %@", error);
        } else {
            [tlAsset setFrontCoverPicture:imageData];
        }
    }
    
    [tlAsset save];
    
    NSDictionary *dict = @{ @"artist": artist, @"title": title, @"album": album };
    resolve(dict);
}

@end
