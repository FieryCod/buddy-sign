;; Copyright 2014-2015 Andrey Antukh <niwi@niwi.nz>
;;
;; Licensed under the Apache License, Version 2.0 (the "License")
;; you may not use this file except in compliance with the License.
;; You may obtain a copy of the License at
;;
;;     http://www.apache.org/licenses/LICENSE-2.0
;;
;; Unless required by applicable law or agreed to in writing, software
;; distributed under the License is distributed on an "AS IS" BASIS,
;; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
;; See the License for the specific language governing permissions and
;; limitations under the License.

(ns buddy.sign.jws-tests
  (:require [clojure.test :refer :all]
            [buddy.core.codecs :as codecs]
            [buddy.core.crypto :as crypto]
            [buddy.core.keys :as keys]
            [buddy.core.bytes :as bytes]
            [buddy.core.nonce :as nonce]
            [buddy.sign.jws :as jws]
            [buddy.sign.util :as util]))

(def secret "test")
(def rsa-privkey (keys/private-key "test/_files/privkey.3des.rsa.pem" "secret"))
(def rsa-pubkey (keys/public-key "test/_files/pubkey.3des.rsa.pem"))
(def ec-privkey (keys/private-key "test/_files/privkey.ecdsa.pem" "secret"))
(def ec-pubkey (keys/public-key "test/_files/pubkey.ecdsa.pem"))

(defn- unsign-exp-fail
  ([signed]
   (unsign-exp-fail signed {}))
  ([signed opts]
   (try
     (jws/unsign signed secret opts)
     (throw (Exception. "unexpected"))
     (catch clojure.lang.ExceptionInfo e
       (:cause (ex-data e))))))

(deftest jws-decode
  (let [candidate {:foo "bar"}
        exp (+ (util/timestamp) 2)
        signed (jws/encode candidate secret {:exp exp})
        unsigned (jws/decode signed secret)]
    (is (= unsigned (assoc candidate :exp exp)))))

(deftest jws-time-claims-validation
  (testing ":exp claim validation"
    (let [candidate {:foo "bar"}
          now       (util/timestamp)
          exp       (+ now 2)
          signed    (jws/encode candidate secret {:exp exp})
          unsigned  (jws/decode signed secret)]
      (Thread/sleep 3000)
      (is (= (unsign-exp-fail signed) :exp))))

  (testing ":nbf claim validation"
    (let [candidate {:foo "bar"}
          now       (util/timestamp)
          nbf       (+ now 2)
          signed    (jws/sign candidate secret {:nbf nbf})]
      (is (= (unsign-exp-fail signed) :nbf))

      (Thread/sleep 3000)
      (let[unsigned  (jws/unsign signed secret)]
        (is (= unsigned (assoc candidate :nbf nbf)))))))

(deftest jws-other-claims-validation
  (testing ":iss claim validation"
    (let [candidate {:foo "bar" :iss "foo:bar"}
          signed    (jws/sign candidate secret)
          unsigned  (jws/unsign signed secret)]
      (is (= unsigned candidate))
      (is (= (unsign-exp-fail signed {:iss "bar:foo"}) :iss))))

  (testing ":aud claim validation"
    (let [candidate {:foo "bar" :aud "foo:bar"}
          signed    (jws/sign candidate secret)
          unsigned  (jws/unsign signed secret)]
      (is (= unsigned candidate))
      (is (= (unsign-exp-fail signed {:aud "bar:foo"}) :aud)))))

(deftest jws-hs256-sign-unsign
  (let [candidate {:foo "bar"}
        result    (jws/sign candidate secret {:alg :hs256})
        result'   (jws/unsign result secret {:alg :hs256})]
    (is (= result' candidate))))

(deftest jws-hs512-sign-unsign
  (let [candidate {:foo "bar"}
        result    (jws/sign candidate secret {:alg :hs512})
        result'   (jws/unsign result secret {:alg :hs512})]
    (is (= result' candidate))))

(deftest jws-rs256-sign-unsign
  (let [candidate {:foo "bar"}
        result    (jws/sign candidate rsa-privkey {:alg :rs256})
        result'   (jws/unsign result rsa-pubkey {:alg :rs256})]
    (is (= result' candidate))))

(deftest jws-rs512-sign-unsign
  (let [candidate {:foo "bar"}
        result    (jws/sign candidate rsa-privkey {:alg :rs512})
        result'   (jws/unsign result rsa-pubkey {:alg :rs512})]
    (is (= result' candidate))))


(deftest jws-ps256-sign-unsign
  (let [candidate {:foo "bar"}
        result    (jws/sign candidate rsa-privkey {:alg :ps256})
        result'   (jws/unsign result rsa-pubkey {:alg :ps256})]
    (is (= result' candidate))))

(deftest jws-ps512-sign-unsign
  (let [candidate {:foo "bar"}
        result    (jws/sign candidate rsa-privkey {:alg :ps512})
        result'   (jws/unsign result rsa-pubkey {:alg :ps512})]
    (is (= result' candidate))))

(deftest jws-es256-sign-unsign
  (let [candidate {:foo "bar"}
        result    (jws/sign candidate ec-privkey {:alg :es256})
        result'   (jws/unsign result ec-pubkey {:alg :es256})]
    (is (= result' candidate))))

(deftest jws-es512-sign-unsign
  (let [candidate {:foo "bar"}
        result    (jws/sign candidate ec-privkey {:alg :es512})
        result'   (jws/unsign result ec-pubkey {:alg :es512})]
    (is (= result' candidate))))

(deftest jws-wrong-key
  (let [candidate {:foo "bar"}
        result    (jws/sign candidate ec-privkey {:alg :es512})]
    (is (= (unsign-exp-fail result) :header))))

(deftest wrong-data
  ;; (str "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXUyJ9."
  ;;      "eyJmb28iOiJiYXIifQ."
  ;;      "FvlogSd-xDr6o2zKLNfNDbREbCf1TcQri3N7LkvRYDs")

  (is (= (unsign-exp-fail "xyz") :signature))
  (let [data (str "."
                  "eyJmb28iOiJiYXIifQ."
                  "FvlogSd-xDr6o2zKLNfNDbREbCf1TcQri3N7LkvRYDs")]
    (is (= (unsign-exp-fail data) :signature)))
  (let [data (str "eyJmb28iOiJiYXIifQ."
                  "FvlogSd-xDr6o2zKLNfNDbREbCf1TcQri3N7LkvRYDs")]
    (is (= (unsign-exp-fail data) :signature))))

