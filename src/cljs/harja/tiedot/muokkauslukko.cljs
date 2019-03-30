(ns harja.tiedot.muokkauslukko
  "Geneerisen muokkauslukon hallinta"
  (:require [reagent.core :refer [atom]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.tiedot.istunto :as istunto]
            [harja.asiakas.tapahtumat :as t]
            [cljs.core.async :refer [<! >! timeout chan close!]]
            [harja.loki :refer [log tarkkaile!]])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]))

; Kun tietyn näkymän lukkoa pyydetään, se asetetaan tähän atomiin.
; Oletetaan, että käyttäjä voi lukita vain yhden näkymän kerrallaan.
(def nykyinen-lukko (atom nil))
(def pollaus-kaynnissa (atom false))

(declare paivita-lukko)

(tarkkaile! "[LUKKO] Nykyinen lukko: " nykyinen-lukko)

(defn- kayttaja-omistaa-lukon? [lukko]
  (= (:kayttaja lukko) (:id @istunto/kayttaja)))

(defn lukko-olemassa? [lukko]
  (and (some? lukko)
       (not= lukko :ei-lukittu)))

(defn nakyma-lukittu? [lukko]
  (if (lukko-olemassa? lukko)
    (do
      (let [kayttajan-oma-lukko (kayttaja-omistaa-lukon? lukko)]
        (log "[LUKKO] Käyttäjä omistaa lukon: " kayttajan-oma-lukko)
        (false? kayttajan-oma-lukko)))
    (do
      (log "[LUKKO] Nykyistä lukkoa ei ole. Näkymä ei ole lukittu.")
      false)))

(defn nykyinen-nakyma-lukittu? []
  (nakyma-lukittu? @nykyinen-lukko))

(defn muodosta-lukon-id
  "Ottaa näkymän ja item-id:n, joilla muodostetaan lukon id.
  nakyma Näkymän nimi, joka halutaan lukita. Esim. paallystysilmoitus.
  item-id Vapaaehtoinen lukittavan itemin id (tämä on sama id jolla item yksilöidään tietokannassa)."
  ([nakyma]
   nakyma)
  ([nakyma item-id]
   (assert (and nakyma item-id) "Lukon id:n muodostukseen vaaditaan näkymä ja item-id!")
   (str nakyma "_" item-id)))

(defn- hae-lukko-idlla [lukko-id]
  (k/post! :hae-lukko-idlla {:id lukko-id}))

(defn- lukitse
  "Merkitsee tietyn näkymän lukituksi, tarkoituksena että vain näkymän lukinnut käyttäjä voi muokata sitä."
  [id]
  (k/post! :lukitse {:id id}))

(defn- virkista-nykyinen-lukko [lukko-id]
  (go
    (reset! nykyinen-lukko (<! (k/post! :virkista-lukko {:id lukko-id})))))

(defn vapauta-lukko [lukko-id]
  (if (kayttaja-omistaa-lukon? @nykyinen-lukko)
    (k/post! :vapauta-lukko {:id lukko-id}))
  (reset! nykyinen-lukko nil)
  (log "[LUKKO] Lukko vapautettu. Uusi lukon tila: " (pr-str @nykyinen-lukko)))

(defn- pollaa []
  (if @nykyinen-lukko
    (do
      (let [lukko-id (:id @nykyinen-lukko)]
        (when (kayttaja-omistaa-lukon? @nykyinen-lukko)
          (virkista-nykyinen-lukko lukko-id))))
          ;; Jos käyttäjä odottaa toisen käyttäjän lukon vapautumista, voitaisiin
          ;; lukkoa pollata ja sen vapautuessa lukita näkymä tälle käyttäjälle.
          ;; Tällöin pitäisi kuitenkin päivittää näkymään uudet tiedot.
          ;; Toistaiseksi hyväksytään, että käyttäjän pitää palata näkymään takaisin kun
          ;; lukko on vapautunut ja uudet tiedot saatavilla.
    ))

(defn- aloita-pollaus []
  (if (false? @pollaus-kaynnissa)
    (go
      (reset! pollaus-kaynnissa true)
      (loop []
        (<! (timeout 50000))
        (if (not (nil? @nykyinen-lukko))
          (do
            (pollaa)
            (recur))
          (do
            (log "[LUKKO] Lopetetaan muokkauslukon pollaus")
            (reset! pollaus-kaynnissa false)))))))

(defn paivita-lukko
  "Hakee lukon kannasta valitulla id:lla. Jos sitä ei ole, luo uuden."
  [lukko-id]
  (go
      (let [vanha-lukko (<! (hae-lukko-idlla lukko-id))]
        (if (lukko-olemassa? vanha-lukko)
          (do
            (reset! nykyinen-lukko vanha-lukko)
            (aloita-pollaus))
          (do
            (let [uusi-lukko (<! (lukitse lukko-id))]
              (if (lukko-olemassa? uusi-lukko)
                (do
                  (reset! nykyinen-lukko uusi-lukko)
                  (aloita-pollaus))
                (do
                    (paivita-lukko lukko-id)))))))))
