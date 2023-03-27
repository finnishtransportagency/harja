(ns harja.tuck-remoting.ilmoitukset-eventit
  (:require #?(:clj
               [tuck.remoting :refer [define-server-event define-client-event]]
               :cljs
               [tuck.remoting :refer-macros [define-server-event define-client-event]])))

(defrecord Ilmoitus [ilmoitus])
(define-client-event Ilmoitus)

(defrecord KuunteleIlmoituksia [opts])
(define-server-event KuunteleIlmoituksia {})

(defrecord LopetaIlmoitustenKuuntelu [])
(define-server-event LopetaIlmoitustenKuuntelu {})
