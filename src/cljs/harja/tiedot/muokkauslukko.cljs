(ns harja.tiedot.muokkauslukko
  "Geneerisen muokkauslukon hallinta"
  (:require [reagent.core :refer [atom]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.tiedot.istunto :as istunto]
            [harja.asiakas.tapahtumat :as t]
            [harja.loki :refer [tarkkaile!]]
            [harja.loki :refer [log tarkkaile!]]

            [cljs.core.async :refer [chan <! >! close!]])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]))

(defn nykyinen-lukko (atom nil))

(defn- kayttaja-omistaa-lukon? [lukko]
  (= (:kayttaja lukko) (:id istunto/kayttaja)))

(defn valittu-nakyma-lukittu? []
  (if (nil? nykyinen-lukko)
    false
    (kayttaja-omistaa-lukon? nykyinen-lukko)))

(defn muodosta-lukon-id
  "Ottaa näkymän ja item-id:n, joilla muodostetaan lukon id.
  nakyma Näkymän nimi, joka halutaan lukita. Esim. paallystysilmoitus.
  item-id Vapaaehtoinen lukittavan itemin id (tämä on sama id jolla item yksilöidään tietokannassa)."
  ([nakyma]
   nakyma)
  ([nakyma item-id]
   (str nakyma "_" item-id)))

(defn- hae-lukko-idlla [lukko-id]
  (k/post! :hae-lukko-idlla {:id lukko-id}))

(defn- lukitse
  "Merkitsee tietyn näkymän lukituksi, tarkoituksena että vain näkymän lukinnut käyttäjä voi muokata sitä."
  [id]
  (k/post! :lukitse {:id id}))

(defn virkista-lukko [lukko-id]
  (k/post! :virkista-lukko {:id lukko-id}))

(defn vapauta-lukko [lukko-id]
  (k/post! :vapauta-lukko {:id lukko-id}))

(defn paivita-lukko
  "Hakee lukon kannasta valitulla id:lla. Jos sitä ei ole, luo uuden."
  [lukko-id]
  (log "Tarkistetaan lukon " lukko-id " tila")
  (let [vanha-lukko (<! (hae-lukko-idlla lukko-id))]
    (log "Lukko saatu: " (pr-str vanha-lukko))
    (if vanha-lukko
      (reset! nykyinen-lukko vanha-lukko)
      (do
        (log "Annetulla id:llä ei ole lukkoa. Yritetään lukita näkymä.")
        (let [uusi-lukko (<! (lukitse lukko-id))]
          (if uusi-lukko
            (reset! nykyinen-lukko uusi-lukko)
            (do (log "Lukitus epäonnistui!")
                (reset! nykyinen-lukko nil))))))))