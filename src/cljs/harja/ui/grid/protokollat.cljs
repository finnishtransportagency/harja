(ns harja.ui.grid.protokollat
  "Gridin ja muokkausgridin yhteiset asiat."
  (:require [reagent.core :refer [atom] :as r]
            [harja.loki :refer [log tarkkaile! logt] :refer-macros [mittaa-aika]]
            [harja.ui.ikonit :as ikonit]
            [cljs.core.async :refer [<! put! chan]]
            [cljs-time.core :as t])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]
                   [harja.makrot :refer [fnc]]))

(def muokkauksessa-olevat-gridit (atom #{}))
(def seuraava-grid-id (atom 0))
(def gridia-muokataan? (reaction (not (empty? @muokkauksessa-olevat-gridit)))) ;; Tarkoitus on, että vain yhtä gridiä muokataan kerralla
(def +rivimaara-jonka-jalkeen-napit-alaskin+ 20)

;; Otsikot
;; Rivi gridin datassa voi olla Otsikko record, jolloin se näytetään väliotsikkona.

(defrecord Otsikko [teksti optiot])

(defn otsikko
  "Luo otsikon annetulla tekstillä.

  Optiot on map, jossa voi olla:
  :id                             Otsikkorivin yksilöivä id (vaaditaan jos otsikkorivit halutaan piilottaa gridissä)
                                  Jos ei anneta, generoidaan uniikki id.
  :otsikkokomponentit             Vector mappeja, jossa avaimina sijainti, sisalto ja tyyli.
                                  Sijainti annetaan prosentteina X-akselilla ja sisalto on funktio,
                                  joka palauttaa komponentin."
  ([teksti] (otsikko teksti {}))
  ([teksti optiot]
   (assert (not (nil? teksti)) "Anna otsikolle teksti.")
   (let [id (or (:id optiot)
                (str (str "harja-grid-valiotsikko-" (gensym))))]
     (->Otsikko teksti (merge {:id id} optiot)))))

(defn otsikko? [x]
  (instance? Otsikko x))

;; Ohjausprotokolla
;; Grid protokolla määrittelee protokollan, jolla gridin toimintoja voi ohjata
;; ulkopuolelta.

(defprotocol Grid
  "Ohjausprotokolla, jolla gridin muokkaustilaa voidaan kysellä ja manipuloida."

  (lisaa-rivi! [g rivin-tiedot] "Lisää muokkaustilassa uuden rivin.
Annettu rivin-tiedot voi olla tyhjä tai se voi alustaa kenttien arvoja.")

  (hae-muokkaustila [g] "Hakee tämänhetkisen muokkaustilan, joka on mäppi id:stä rivin tietoihin.")

  (aseta-muokkaustila! [g muokkaustila] "Asettaa uuden muokkaustilan säilyttäen historian")

  (hae-virheet [g] "Hakee tämänhetkisen muokkaustilan mukaiset validointivirheet.")

  (hae-varoitukset [g] "Hakee tämänhetkisen muokkaustilan mukaiset validointivaroitukset.")

  (hae-huomautukset [g] "Hakee tämänhetkisen muokkaustilan mukaiset huomautukset.")

  (nollaa-historia! [g] "Nollaa muokkaushistorian, tämä on lähinnä muokkaus-grid versiota varten. Tällä voi kertoa gridille, että data on täysin muuttunut eikä muokkaushistoria ole enää relevantti.")
  ;; PENDING: oisko "jemmaa muokkaushistoria", jolla sen saisi avaimella talteen ja otettua takaisin?
  (hae-viimeisin-muokattu-id [g] "Hakee viimeisimmän muokatun id:n")
  (muokkaa-rivit! [this funktio args] "Muokkaa kaikki taulukon rivit funktion avulla.")

  (vetolaatikko-auki? [this id] "Tarkista onko vetolaatikko auki annetulla rivin id:llä.")

  (avaa-vetolaatikko! [this id] "Avaa vetolaatikko rivin id:llä.")

  (sulje-vetolaatikko! [this id] "sulje vetolaatikko rivin id:llä.")

  (sulje-vetolaatikot! [this] "Sulje kaikki vetolaatikot")

  (avattavat-rivit-auki? [this id] "Tarkista onko avattavat-rivit auki annetulla rivin id:llä.")

  (avaa-avattavat-rivit! [this id] "Avaa avattavat-rivit rivin id:llä.")

  (sulje-avattavat-rivit! [this id] "sulje avattavat-rivit rivin id:llä.")

  (aseta-virhe! [this rivin-id kentta virheteksti] "Asettaa ulkoisesti virheen rivin kentälle")
  (poista-virhe! [this rivin-id kentta] "Poistaa rivin kentän virheen ulkoisesti")
  (validoi-grid [this] "Validoi gridin"))

(defprotocol GridKahva
  "Sisäinen protokolla, jolle grid asettaa itsensä."
  (aseta-grid [this grid] "Asetaa gridin, jolle ohjauskutsut pitäisi välittää."))

(defn grid-ohjaus
  "Tekee grid ohjausinstanssin, jonka kutsuva puoli voi antaa gridille."
  []
  (let [gridi (atom nil)]
    (reify
      Grid
      (lisaa-rivi! [_ rivin-tiedot]
        (lisaa-rivi! @gridi rivin-tiedot))

      (hae-muokkaustila [_]
        (hae-muokkaustila @gridi))

      (aseta-muokkaustila! [_ tila]
        (aseta-muokkaustila! @gridi tila))

      (hae-virheet [_]
        (hae-virheet @gridi))

      (hae-varoitukset [_]
        (hae-varoitukset @gridi))

      (hae-huomautukset [_]
        (hae-huomautukset @gridi))

      (nollaa-historia! [_]
        (nollaa-historia! @gridi))

      (hae-viimeisin-muokattu-id [_]
        (hae-viimeisin-muokattu-id @gridi))

      (muokkaa-rivit! [this funktio args]
        (muokkaa-rivit! @gridi funktio args))

      (vetolaatikko-auki? [_ id]
        (vetolaatikko-auki? @gridi id))

      (avaa-vetolaatikko! [_ id]
        (avaa-vetolaatikko! @gridi id))

      (sulje-vetolaatikko! [_ id]
        (sulje-vetolaatikko! @gridi id))

      (sulje-vetolaatikot! [_]
        (sulje-vetolaatikot! @gridi))

      (avattavat-rivit-auki? [_ id]
        (avattavat-rivit-auki? @gridi id))

      (avaa-avattavat-rivit! [_ id]
        (avaa-avattavat-rivit! @gridi id))

      (sulje-avattavat-rivit! [_ id]
        (sulje-avattavat-rivit! @gridi id))

      (aseta-virhe! [_ rivin-id kentta virheteksti]
        (aseta-virhe! @gridi rivin-id kentta virheteksti))
      (poista-virhe! [_ rivin-id kentta]
        (poista-virhe! @gridi rivin-id kentta))
      (validoi-grid [_]
        (validoi-grid @gridi))

      GridKahva
      (aseta-grid [_ grid]
        (reset! gridi grid)))))

(defn vetolaatikko-rivi
  "Funktio, joka palauttaa vetolaatikkorivin tai nil. Huom: kutsu tätä funktiona, koska voi palauttaa nil."
  ([vetolaatikot vetolaatikot-auki id colspan]
   (vetolaatikko-rivi vetolaatikot vetolaatikot-auki id colspan {}))
  ([vetolaatikot vetolaatikot-auki id colspan {:keys [ei-paddingia] :as optiot}]
   (when-let [vetolaatikko (get vetolaatikot id)]
     (let [auki (@vetolaatikot-auki id)]
       ^{:key (str "vetolaatikko" id)}
       [:tr.vetolaatikko {:class (apply conj #{} (keep identity [(if auki "vetolaatikko-auki" "vetolaatikko-kiinni")]) )}
        [:td {:colSpan colspan
              :class (apply conj #{} (keep identity [(when ei-paddingia "vetolaatikko-ei-paddingia")]))}
         [:div.vetolaatikko-sisalto
          (when auki
            vetolaatikko)]]]))))

(defn avaa-tai-sulje-vetolaatikko!
  "Vaihtaa vetolaatikon tilaa. Avaa vetolaatikon, jos se on suljettu, muuten sulkee sen."
  [g id]
  (if (vetolaatikko-auki? g id)
    (sulje-vetolaatikko! g id)
    (avaa-vetolaatikko! g id)))

(defn avaa-tai-sulje-avattavat-rivit!
  "Vaihtaa avattavien rivien tilaa. Avaa avattavat rivit, jos se on suljettu, muuten sulkee sen."
  [g id]
  (if (avattavat-rivit-auki? g id)
    (sulje-avattavat-rivit! g id)
    (avaa-avattavat-rivit! g id)))

(defn vetolaatikon-tila [ohjaus vetolaatikot id luokat]
  (let [vetolaatikko? (contains? vetolaatikot id)]
    ^{:key (str "vetolaatikontila" id)}
    [:td {:class (if (coll? luokat) luokat [luokat])}
     (when vetolaatikko?
       [:button.vetolaatikon-sailio
        {:on-click #(do
                      (.preventDefault %)
                      (.stopPropagation %)
                      (avaa-tai-sulje-vetolaatikko! ohjaus id))
         :on-blur #(do
                     ;; Estetään mahdollinen rivin blur-efekti vetolaatikon avaamisen tai sulkemisen jälkeen
                     (.preventDefault %)
                     (.stopPropagation %))}
        [:div.inline-block.vetolaatikon-pylvas]
        (if (vetolaatikko-auki? ohjaus id)
          [ikonit/navigation-ympyrassa :down]
          [ikonit/navigation-ympyrassa :right])])]))

(defn avattavat-rivi-tila [ohjaus avattavat-rivit id luokat]
  (let [avattava-rivi? (contains? avattavat-rivit id)]
    ^{:key (str "avattava-rivi-tila" id)}
    [:td {:class (str luokat)}
     (when avattava-rivi?
       [:button.vetolaatikon-sailio
        {:on-click #(do
                      (.preventDefault %)
                      (.stopPropagation %)
                      (avaa-tai-sulje-avattavat-rivit! ohjaus id))}
        [:div.inline-block.vetolaatikon-pylvas]
        (if (avattavat-rivit-auki? ohjaus id)
          [ikonit/navigation-ympyrassa :down]
          [ikonit/navigation-ympyrassa :right])])]))
