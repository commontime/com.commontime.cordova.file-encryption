#import "FileEncryption.h"
#import <AVFoundation/AVFoundation.h>

NSString* const ENCRYPT_FILE_MESSAGE_ID = @"ENCRYPT_FILE";

@implementation FileEncryption

- (void) pluginInitialize
{
    [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(encryptFileNotification:) name:ENCRYPT_FILE_MESSAGE_ID object:nil];
}

- (void)encryptFileNotification:(NSNotification *)notification
{
    CustomCDVInvokedUrlCommand *newCommand = [[CustomCDVInvokedUrlCommand alloc] initWithArguments:notification.object callbackId:nil className:nil methodName:nil];
    [self encrypt:newCommand];
}

- (void)encrypt:(CDVInvokedUrlCommand*)command
{
    CDVPluginResult *pluginResult = nil;
    
    NSString *path = [self crypto:@"encrypt" command:command];
        
    if (path != nil) {
        pluginResult =
        [CDVPluginResult resultWithStatus:CDVCommandStatus_OK
                  messageAsString:path];
    } else {
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR];
    }
    
    if ([command isKindOfClass:[CustomCDVInvokedUrlCommand class]]) {
        [[NSNotificationCenter defaultCenter] postNotificationName:[command.arguments objectAtIndex:1] object:path];
    } else {
        [self.commandDelegate sendPluginResult:pluginResult
                                callbackId:command.callbackId];
    }
}

- (void)decrypt:(CDVInvokedUrlCommand *)command {
    
    CDVPluginResult *pluginResult = nil;
    
    NSString *path = [self crypto:@"decrypt" command:command];
        
    if (path != nil) {
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:path];
    } else {
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR];
    }
    
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

- (void)getFileSize:(CDVInvokedUrlCommand*)command
{
    CDVPluginResult *pluginResult = nil;
    
    NSString *path = [command.arguments objectAtIndex:0];
    path = [path stringByReplacingOccurrencesOfString:@"file://" withString:@""];
    path = [path stringByReplacingOccurrencesOfString:@"file:" withString:@""];
    
    if (path) {
        
        NSError *error = nil;
        
        NSDictionary *fileAttributes = [[NSFileManager defaultManager] attributesOfItemAtPath:path error:&error];
        
        if (error) {
            pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR];
        } else {
            NSNumber *fileSizeNumber = [fileAttributes objectForKey:NSFileSize];
            long long fileSize = [fileSizeNumber longLongValue];
            pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:[NSString stringWithFormat:@"%lld", fileSize]];
        }
        
    } else {
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR];
    }
    
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

- (void)getAudioFileDuration:(CDVInvokedUrlCommand*)command
{
    CDVPluginResult *pluginResult = nil;
    
    NSString *path = [command.arguments objectAtIndex:0];
    
    if (path) {
        
        NSError *error = nil;
        AVAudioPlayer* avAudioPlayer = [[AVAudioPlayer alloc] initWithContentsOfURL:[NSURL URLWithString:path] error:&error];
        
        if (error) {
            pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR];
        } else {
            pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDouble:avAudioPlayer.duration];
        }
        
        avAudioPlayer = nil;
        
    } else {
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR];
    }
    
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

- (NSString*)crypto:(NSString *)action command:(CDVInvokedUrlCommand *)command {
    
    NSString *path = [command.arguments objectAtIndex:0];
    path = [path stringByReplacingOccurrencesOfString:@"file://" withString:@""];
    NSFileManager *fileManager = [NSFileManager defaultManager];
    BOOL fileExists = [fileManager fileExistsAtPath:path];
    
    if (path != nil && [path length] > 0) {

        if (fileExists) {
            
            if ([action isEqualToString:@"encrypt"]) {
                
                NSData *fileData = [NSData dataWithContentsOfFile:path];
                [fileManager createFileAtPath:path contents:fileData attributes:[NSDictionary dictionaryWithObject:NSFileProtectionComplete forKey:NSFileProtectionKey]];
                return path;
                
            } else if ([action isEqualToString:@"decrypt"]) {
                
                return path;
            }
            
        } else {
            path = nil;
        }
    }
    
    return nil;
}

@end

@implementation CustomCDVInvokedUrlCommand
@end