(ns harja.tuck-remoting.ilmoitukset-eventit
  (:require #?(:clj
               [tuck.remoting :refer [define-server-event define-client-event]]
               :cljs
               [tuck.remoting :refer-macros [define-server-event define-client-event]])))

(defrecord Ilmoitus [opts])
(define-client-event Ilmoitus)
