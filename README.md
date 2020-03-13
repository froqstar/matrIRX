# matrIRX

## What is this?

This is a pipe that connects an IRC channel to a Matrix channel. 
That is, it posts all messages posted to a given IRC channel to a given Matrix channel, and vise versa. 
As a result, you can make an IRC channel available to Matrix clients.

Also, it is super ugly and hacky code. My apologies to the reader.

## How does it do it?

Internally, it just uses two bots: One IRC bot, and one Matrix bot, and exchanges the posted messages between them.
For every Matrix client in the given channel, it dynamically creates a new IRC user so that Matrix users are represented in the IRC channel with their original name.
This doesn't work the other way round, since you can't (or shouldn't?) just dynamically create a new Matrix user for every IRC user in the IRC channel. So messages coming from the IRC channel are posted from a single Matrix user, but prefixed with the IRC username.

## Why do you need it?

IRC has severe limitations that make it nearly unusable for people unable/unwilling to run their own infrastructure.
Mainly, this is that you lose messages that are posted to an IRC channel while you are offline. 
Also, you can't join from multiple client devices with the same nick.

Yes, I know you can solve both by running your own bouncer. But as said, it can't be expected of everybody who wants to 
participate in longer discussions over IRC to run their own infrastructure. 

Matrix doesn't have those limitations, plus a lot of other advantages,
so [until we live in a better world](https://xkcd.com/1782/), this is my attempt on making IRC usable for everyday people
by only requiring one instance of this pipe to be running instead of one per user.

## How can you use it?

TODO

## What are the limitations?

- No DMs implemented (and a concept missing completely)
- Performance? (not tested at all)
