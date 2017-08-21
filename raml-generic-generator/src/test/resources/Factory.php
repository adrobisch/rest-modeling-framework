<?php

namespace Test\Client;

use Cache\Adapter\Filesystem\FilesystemCachePool;
use Test\Client\Subscriber\Log\Formatter;
use Test\Client\Subscriber\Log\LogSubscriber;
use GuzzleHttp\Client as HttpClient;
use GuzzleHttp\Event\BeforeEvent;
use GuzzleHttp\HandlerStack;
use GuzzleHttp\MessageFormatter;
use GuzzleHttp\Middleware;
use GuzzleHttp\Psr7\Response;
use League\Flysystem\Adapter\Local;
use League\Flysystem\Filesystem;
use Psr\Cache\CacheItemPoolInterface;
use Psr\Http\Message\RequestInterface;
use GuzzleHttp\Message\RequestInterface as GuzzleRequestInterface;
use GuzzleHttp\Message\ResponseInterface as GuzzleResponseInferface;
use Psr\Http\Message\ResponseInterface;
use Psr\Log\LoggerInterface;
use Psr\Log\LogLevel;

class Factory
{
    const API_URI = 'apiUri';
    const AUTH_URI = 'https://auth.example.com/oauth/token';

    private static $guzzle6;

    /**
     * @param $options
     * @param LoggerInterface $logger
     * @param CacheItemPoolInterface $cache
     * @param TokenProvider $provider
     * @return HttpClient
     */
    public static function create(
        $options,
        LoggerInterface $logger = null,
        CacheItemPoolInterface $cache = null,
        TokenProvider$provider = null
    ) {
        $factory = new static();

        return $factory->createClient($options, $logger, $cache, $provider);
    }

    /**
     * @param $options
     * @param LoggerInterface $logger
     * @param CacheItemPoolInterface $cache
     * @param TokenProvider $provider
     * @return HttpClient
     */
    public function createClient(
        array $options = [],
        LoggerInterface $logger = null,
        CacheItemPoolInterface $cache = null,
        TokenProvider $provider = null
    ) {
        if (is_null($cache)) {
            if (isset($options['cacheDir'])) {
                $cacheDir = $options['cacheDir'];
                unset($options['cacheDir']);
            } else {
                $cacheDir = getcwd();
            }

            $filesystemAdapter = new Local($cacheDir);
            $filesystem        = new Filesystem($filesystemAdapter);
            $cache = new FilesystemCachePool($filesystem);
        }
        $credentials = [];
        if (isset($options['credentials'])) {
            $credentials = $options['credentials'];
            unset($options['credentials']);
        }
        if (!isset($options['base_uri'])) {
            $options['base_uri'] = self::API_URI;
        }
        $oauthHandler = $this->getHandler($credentials, self::AUTH_URI, $cache, $provider);

        if (self::isGuzzle6()) {
            return $this->createGuzzle6Client($options, $logger, $oauthHandler);
        } else {
            return $this->createGuzzle5Client($options, $logger, $oauthHandler);
        }
    }

    /**
     * @param $options
     * @param LoggerInterface|null $logger
     * @param OAuth2Handler $oauthHandler
     * @return HttpClient
     */
    private function createGuzzle6Client(
        $options,
        LoggerInterface $logger = null,
        OAuth2Handler $oauthHandler
    ) {
        if (!isset($options['handler'])) {
            $handler = HandlerStack::create();
            $options['handler'] = $handler;
        } else {
            $handler = $options['handler'];
        }

        $options = array_merge(
            [
                'allow_redirects' => false,
                'verify' => true,
                'timeout' => 60,
                'connect_timeout' => 10,
                'pool_size' => 25
            ],
            $options
        );

        if (!is_null($logger)) {
            $handler->push(Middleware::log($logger, new MessageFormatter()));
        }
        $handler->push(
            Middleware::mapRequest($oauthHandler),
            'oauth_2_0'
        );

        $client = new HttpClient($options);

        return $client;
    }


    /**
     * @param $options
     * @param LoggerInterface|null $logger
     * @param OAuth2Handler $oauthHandler
     * @return HttpClient
     */
    private function createGuzzle5Client(
        $options,
        LoggerInterface $logger = null,
        OAuth2Handler $oauthHandler
    ) {
        if (isset($options['base_uri'])) {
            $options['base_url'] = $options['base_uri'];
            unset($options['base_uri']);
        }
        if (isset($options['headers'])) {
            $options['defaults']['headers'] = $options['headers'];
            unset($options['headers']);
        }
        $options = array_merge(
            [
                'allow_redirects' => false,
                'verify' => true,
                'timeout' => 60,
                'connect_timeout' => 10,
                'pool_size' => 25
            ],
            $options
        );
        $client = new HttpClient($options);
        if (!is_null($logger)) {
            if ($logger instanceof LoggerInterface) {
                $formatter = new Formatter();
                $client->getEmitter()->attach(new LogSubscriber($logger, $formatter, LogLevel::INFO));
            }
        }

        $client->getEmitter()->on('before', function (BeforeEvent $e) use ($oauthHandler) {
            $e->getRequest()->setHeader('Authorization', $oauthHandler->getAuthorizationHeader());
        });

        return $client;
    }

    /**
     * @param RequestInterface $psrRequest
     * @param HttpClient $client
     * @return GuzzleRequestInterface|RequestInterface
     */
    public static function createRequest(RequestInterface $psrRequest, HttpClient $client)
    {
        if (self::isGuzzle6()) {
            return $psrRequest;
        }
        $options = [
            'headers' => $psrRequest->getHeaders(),
            'body' => (string)$psrRequest->getBody()
        ];

        return $client->createRequest($psrRequest->getMethod(), (string)$psrRequest->getUri(), $options);
    }

    public static function createResponse($response)
    {
        if ($response instanceof ResponseInterface) {
            return $response;
        }
        if ($response instanceof GuzzleResponseInferface) {
            return new Response(
                $response->getStatusCode(),
                $response->getHeaders(),
                (string)$response->getBody()
            );
        }

        throw new \InvalidArgumentException(
            'Argument 1 must be an instance of Psr\Http\Message\ResponseInterface ' .
            'or GuzzleHttp\Message\ResponseInterface'
        );
    }

    private function getHandler($credentials, $accessTokenUrl, $cache = null, $provider = null)
    {
        if (is_null($provider)) {
            $provider = new TokenProvider(
                new HttpClient(),
                $accessTokenUrl,
                $credentials
            );
        }
        return new OAuth2Handler($provider, $cache);
    }

    private static function isGuzzle6() {
        if (is_null(self::$guzzle6)) {
            if (version_compare(HttpClient::VERSION, '6.0.0', '>=')) {
                self::$guzzle6 = true;
            } else {
                self::$guzzle6 = false;
            }
        }
        return self::$guzzle6;
    }
}
