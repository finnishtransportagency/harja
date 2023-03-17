(ns harja.domain.ws-ilmoitukset
  "Tuck remoting example: chat events"
  (:require [tuck.core :as t :refer [define-event]]
            #?(:clj
               [tuck.remoting :refer [define-server-event define-client-event]]
               :cljs
               [tuck.remoting :refer-macros [define-server-event define-client-event]])))

;; Actions we can do: join, say
(defrecord Join [name])
(defrecord Say [message])

;; Events server notifies us about
(defrecord Joined [name])
(defrecord Parted [name])
(defrecord Message [name time message])

(define-server-event Join {})
(define-server-event Say {})
(define-client-event Message map->Message)
(define-client-event Joined map->Joined)
(define-client-event Parted map->Parted)

;; Server side housekeeping events
(defrecord Disconnected [status])

;; Client side other events
(defrecord UpdateName [name])
(defrecord UpdateComposedMessage [msg])
