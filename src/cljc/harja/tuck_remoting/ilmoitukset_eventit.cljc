(ns harja.tuck-remoting.ilmoitukset-eventit
  (:require #?(:clj
               [tuck.remoting :refer [define-server-event define-client-event]]
               :cljs
               [tuck.remoting :refer-macros [define-server-event define-client-event]])))

(defrecord Ilmoitus [ilmoitus])
(define-client-event Ilmoitus)

(defrecord KuunteleIlmoituksia [suodattimet])
;; Tallenna app-tilaan WS kautta lähetetyn KuunteleIlmoituksia-tapahtuman ID
(define-server-event KuunteleIlmoituksia {:event-id-path [:ws-ilmoitusten-kuuntelu :kuuntele-ilmoituksia-tapahtuma-id]})

(defrecord IlmoitustenKuunteluOnnistui [])
(define-client-event IlmoitustenKuunteluOnnistui)
(defrecord IlmoitustenKuunteluEpaonnistui [])
(define-client-event IlmoitustenKuunteluEpaonnistui)

(defrecord LopetaIlmoitustenKuuntelu [])
(define-server-event LopetaIlmoitustenKuuntelu {})
