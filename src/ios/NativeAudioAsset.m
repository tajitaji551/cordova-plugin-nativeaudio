//
//
//  NativeAudioAsset.m
//  NativeAudioAsset
//
//  Created by Sidney Bofah on 2014-06-26.
//

#import "NativeAudioAsset.h"

@implementation NativeAudioAsset

static const CGFloat FADE_STEP = 0.05;
static const CGFloat FADE_DELAY = 0.08;

-(id) initWithPath:(NSString*) path withVoices:(NSNumber*) numVoices withVolume:(NSNumber*) volume withFadeDelay:(NSNumber *)delay
{
    self = [super init];
    if(self) {
        playCounter = 0;
        voices = [[NSMutableArray alloc] init];

        NSURL *pathURL = [NSURL fileURLWithPath : path];

        for (int x = 0; x < [numVoices intValue]; x++) {
            AVAudioPlayer *player = [[AVAudioPlayer alloc] initWithContentsOfURL:pathURL error: NULL];
            player.volume = volume.floatValue;
            [player prepareToPlay];
            [voices addObject:player];
            [player setDelegate:self];

            if(delay)
            {
                fadeDelay = delay;
            }
            else {
                fadeDelay = [NSNumber numberWithFloat:FADE_DELAY];
            }

            initialVolume = volume;
        }

        playIndex = 0;
    }
    return(self);
}

- (void)play:(NSNumber *)startTime duration:(NSNumber *)duration rate:(NSNumber *)rate
{
    if ((playCounter + 1) > INT_MAX) {
        playCounter = 0;
    } else {
        playCounter++;
    }

    AVAudioPlayer * player = [voices objectAtIndex:playIndex];
    // if isPlayer==YES then stop
    if (player.isPlaying) {
        [player stop];
    }
    // set rate
    player.enableRate = YES;
    player.rate = rate.doubleValue;
    // set currentTime to startTime
    [player setCurrentTime:startTime.doubleValue];
    player.numberOfLoops = 0;
    [player play];
    // stop after duration time
    [self autoStop:player duration:duration counter:playCounter];

    playIndex += 1;
    playIndex = playIndex % [voices count];
}

- (void)autoStop:(AVAudioPlayer *)player duration:(NSNumber *)duration counter:(int)counter
{
    int myCounter = counter;
    if (duration.floatValue > 0.0f) {
        dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (int64_t)(duration.floatValue * NSEC_PER_SEC)), dispatch_get_main_queue(), ^{
            if (player.isPlaying && myCounter == playCounter) {
                [player stop];
            }
        });
    }
}

// The volume is increased repeatedly by the fade step amount until the last step where the audio is stopped.
// The delay determines how fast the decrease happens
- (void)playWithFade
{
    AVAudioPlayer * player = [voices objectAtIndex:playIndex];

    if (!player.isPlaying)
    {
        [player setCurrentTime:0.0];
        player.numberOfLoops = 0;
        player.volume = 0;
        [player play];
        playIndex += 1;
        playIndex = playIndex % [voices count];
        [self performSelector:@selector(playWithFade) withObject:nil afterDelay:fadeDelay.floatValue];
    }
    else
    {
        if(player.volume < initialVolume.floatValue)
        {
            player.volume += FADE_STEP;
            [self performSelector:@selector(playWithFade) withObject:nil afterDelay:fadeDelay.floatValue];
        }
    }
}

- (void) stop
{
    for (int x = 0; x < [voices count]; x++) {
        AVAudioPlayer * player = [voices objectAtIndex:x];
        [player stop];
    }
}

// The volume is decreased repeatedly by the fade step amount until the volume reaches the configured level.
// The delay determines how fast the increase happens
- (void)stopWithFade
{
    BOOL shouldContinue = NO;

    for (int x = 0; x < [voices count]; x++) {
        AVAudioPlayer * player = [voices objectAtIndex:x];

        if (player.isPlaying && player.volume > FADE_STEP) {
            player.volume -= FADE_STEP;
            shouldContinue = YES;
        } else {
            // Stop and get the sound ready for playing again
            [player stop];
            player.volume = initialVolume.floatValue;
            player.currentTime = 0;
        }
    }

    if(shouldContinue) {
        [self performSelector:@selector(stopWithFade) withObject:nil afterDelay:fadeDelay.floatValue];
    }
}

- (void) loop
{
    [self stop];
    AVAudioPlayer * player = [voices objectAtIndex:playIndex];
    [player setCurrentTime:0.0];
    player.numberOfLoops = -1;
    [player play];
    playIndex += 1;
    playIndex = playIndex % [voices count];
}

- (void) unload
{
    [self stop];
    for (int x = 0; x < [voices count]; x++) {
        AVAudioPlayer * player = [voices objectAtIndex:x];
        player = nil;
    }
    voices = nil;
}

- (void) setVolume:(NSNumber*) volume;
{

    for (int x = 0; x < [voices count]; x++) {
        AVAudioPlayer * player = [voices objectAtIndex:x];

        [player setVolume:volume.floatValue];
    }
}

- (void) setCallbackAndId:(CompleteCallback)cb audioId:(NSString*)aID
{
    self->audioId = aID;
    self->finished = cb;
}

- (void) audioPlayerDidFinishPlaying:(AVAudioPlayer *)player successfully:(BOOL)flag
{
    if (self->finished) {
        self->finished(self->audioId);
    }
}

- (void) audioPlayerDecodeErrorDidOccur:(AVAudioPlayer *)player error:(NSError *)error
{
    if (self->finished) {
        self->finished(self->audioId);
    }
}

@end
