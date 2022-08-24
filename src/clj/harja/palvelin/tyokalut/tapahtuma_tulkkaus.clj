(ns harja.palvelin.tyokalut.tapahtuma-tulkkaus
  (:require [harja.palvelin.integraatiot.jms :as jms]))

(def ^{:doc "Nil arvoa ei voi lähettää kanavaan. Käytetään tätä arvoa
             merkkaamaan 'tyhjää' tapausta. Eli eventin nimi on dataa
             tärkeämpi"}
  tyhja-arvo ::tyhja)

(defn jmsyhteys-ok?
  [tila]
  (jms/jmsyhteys-ok? tila))

