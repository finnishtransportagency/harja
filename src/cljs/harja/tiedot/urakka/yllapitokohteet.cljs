(ns harja.tiedot.urakka.yllapitokohteet
  "Ylläpitokohteiden tiedot"
  (:require
    [harja.loki :refer [log tarkkaile!]]
    [cljs.core.async :refer [<!]]
    [harja.asiakas.kommunikaatio :as k]
    [harja.tiedot.urakka :as urakka]
    [harja.tiedot.urakka :as u]
    [harja.domain.yllapitokohteet :as yllapitokohteet-domain]
    [harja.ui.kartta.esitettavat-asiat :refer [kartalla-esitettavaan-muotoon]]
    [harja.tiedot.navigaatio :as nav]
    [harja.ui.viesti :as viesti]
    [clojure.string :as str])

  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]
                   [harja.atom :refer [reaction<!]]))

(defn yha-kohde? [kohde]
  (some? (:yhaid kohde)))

(defn suodata-yllapitokohteet
  "Suodatusoptiot on map, jolla voi valita halutut suodatusperusteet:
   :tienumero int
   :yha-kohde? boolean
   :yllapitokohdetyotyyppi keyword (:paallystys / :paikkaus)
   :kohdenumero int
   Jos jotain arvoa ei anneta, sitä ei huomioida suodatuksessa"
  [kohteet suodatusoptiot]
  (let [yha-kohde-fn yha-kohde?
        {:keys [tienumero yha-kohde? yllapitokohdetyotyyppi kohdenumero]} suodatusoptiot]
    (filterv
      #(and (or (nil? yha-kohde?) (if yha-kohde? (yha-kohde-fn %) (not (yha-kohde-fn %))))
            (or (nil? tienumero) (= (:tr-numero %) tienumero))
            (or (nil? yllapitokohdetyotyyppi) (= (:yllapitokohdetyotyyppi %) yllapitokohdetyotyyppi))
            (or (str/blank? kohdenumero) (str/blank? (:kohdenumero %))
                (= (str/lower-case (:kohdenumero %))
                   (str/lower-case kohdenumero))))
      kohteet)))

(defn hae-yllapitokohteet [urakka-id sopimus-id vuosi]
  (k/post! :urakan-yllapitokohteet {:urakka-id urakka-id
                                    :sopimus-id sopimus-id
                                    :vuosi vuosi}))


(defn tallenna-yllapitokohteet! [urakka-id sopimus-id vuosi kohteet]
  (k/post! :tallenna-yllapitokohteet {:urakka-id urakka-id
                                      :sopimus-id sopimus-id
                                      :vuosi vuosi
                                      :kohteet kohteet}))

(defn tallenna-yllapitokohdeosat! [urakka-id sopimus-id yllapitokohde-id osat]
  (k/post! :tallenna-yllapitokohdeosat {:urakka-id urakka-id
                                        :sopimus-id sopimus-id
                                        :yllapitokohde-id yllapitokohde-id
                                        :osat osat}))

(defn hae-maaramuutokset [urakka-id yllapitokohde-id]
  (k/post! :hae-maaramuutokset {:urakka-id urakka-id
                                :yllapitokohde-id yllapitokohde-id}))

(defn tallenna-maaramuutokset! [{:keys [urakka-id yllapitokohde-id maaramuutokset
                                        sopimus-id vuosi]}]
  (k/post! :tallenna-maaramuutokset {:urakka-id urakka-id
                                     :sopimus-id sopimus-id
                                     :vuosi vuosi
                                     :yllapitokohde-id yllapitokohde-id
                                     :maaramuutokset maaramuutokset}))

(def alku (juxt :tr-alkuosa :tr-alkuetaisyys))
(def loppu (juxt :tr-loppuosa :tr-loppuetaisyys))

(defn kasittele-paivittyneet-kohdeosat
  "Käsittelee päivittyneen kohdeosien muokkaus-grid datan. Sekä uudet ja vanhat
  ovat mäppejä numerosta riviin. Oletetaan, että yksi avainten arvoista on muuttunut
  käyttäjän tekemän editointioperaation johdosta.
  Jos käyttäjä muokkasi alkuosaa, asetetaan mahdollisen edeltävän rivin loppuosa samaksi.
  Jos käyttäjä muokkasi loppuosaa, asetetaaan seuraavan rivin alkuosa samaksi."
  [vanhat uudet]
  (if-not (= (count vanhat) (count uudet))
    uudet
    (let [riveja (count uudet)
          [muokattu-vanha muokattu-uusi muokattu-key]
          (some #(let [vanha (get vanhat %)
                       uusi (get uudet %)]
                   (when-not (= vanha uusi)
                     [vanha uusi %]))
                (keys uudet))
          edellinen-key (when (> muokattu-key 1)
                          (dec muokattu-key))
          edellinen (when edellinen-key
                      (get uudet edellinen-key))

          seuraava-key (when (< muokattu-key riveja)
                         (inc muokattu-key))
          seuraava (when seuraava-key
                     (get uudet seuraava-key))

          alku-muutettu? (not= (alku muokattu-vanha) (alku muokattu-uusi))
          loppu-muutettu? (not= (loppu muokattu-vanha) (loppu muokattu-uusi))]

      (as-> uudet rivit
            (if alku-muutettu?
              (assoc rivit edellinen-key
                           (merge edellinen
                                  (zipmap [:tr-loppuosa :tr-loppuetaisyys]
                                          (alku muokattu-uusi))))
              rivit)

            (if loppu-muutettu?
              (assoc rivit seuraava-key
                           (merge seuraava
                                  (zipmap [:tr-alkuosa :tr-alkuetaisyys]
                                          (loppu muokattu-uusi))))
              rivit)))))

(defn lisaa-uusi-kohdeosa
  "Lisää uuden kohteen annetussa indeksissä olevan kohteen perään (alapuolelle). Muuttaa kaikkien
  jälkeen tulevien osien avaimia yhdellä suuremmaksi."
  [kohdeosat key]
  (let [rivi (get kohdeosat key)
        avaimet-jalkeen (filter #(> % key) (keys kohdeosat))
        uusi-rivi {:nimi ""
                   :tr-numero (:tr-numero rivi)
                   :tr-alkuosa nil
                   :tr-alkuetaisyys nil
                   :tr-loppuosa (:tr-loppuosa rivi)
                   :tr-loppuetaisyys (:tr-loppuetaisyys rivi)
                   :tr-ajorata (:tr-ajorata rivi)
                   :tr-kaista (:tr-kaista rivi)
                   :toimenpide ""}]
    (-> kohdeosat
        (assoc-in [key :tr-loppuosa] nil)
        (assoc-in [key :tr-loppuetaisyys] nil)
        (assoc (inc key) uusi-rivi)
        (merge (zipmap (map inc avaimet-jalkeen)
                       (map #(get kohdeosat %) avaimet-jalkeen))))))

(defn poista-kohdeosa
  "Poistaa valitun kohdeosan annetulla avaimella. Pidentää edellistä kohdeosaa niin, että sen pituus
  täyttää poistetun kohdeosan jättämän alueen. Jos poistetaan ensimmäinen kohdeosa, pidennetään
  vastaavasti seuraava."
  [kohdeosat key]
  (let [avaimet (sort (keys kohdeosat))
        avaimet-ennen (filter #(< % key) avaimet)
        avaimet-jalkeen (filter #(> % key) avaimet)
        ennen (when-not (empty? avaimet-ennen)
                (get kohdeosat (last avaimet-ennen)))
        kohdeosa (get kohdeosat key)
        jalkeen (when-not (empty? avaimet-jalkeen)
                  (get kohdeosat (first avaimet-jalkeen)))]
    (cond
      ;; Poistetaan ensimmäistä, aseta ensimmäisen alku seuraavan aluksi
      (nil? ennen)
      (merge {1 (merge jalkeen
                       (select-keys kohdeosa [:tr-alkuosa :tr-alkuetaisyys]))}
             (zipmap (iterate inc 2)
                     (map #(get kohdeosat %) (rest avaimet-jalkeen))))

      ;; Poistetaan viimeistä, aseta viimeisen loppu toiseksi viimeiseen
      (nil? jalkeen)
      (merge (zipmap (iterate inc 1)
                     (map #(get kohdeosat %) (butlast avaimet-ennen)))
             {(count avaimet-ennen)
              (merge ennen
                     (select-keys kohdeosa [:tr-loppuosa :tr-loppuetaisyys]))})

      ;; Poistetaan rivi välistä, asetetaan tämän rivin loppuosa seuraavan alkuosaksi
      :default
      (merge (zipmap (iterate inc 1)
                     (map #(get kohdeosat %) avaimet-ennen))
             {(inc (count avaimet-ennen))
              (merge jalkeen
                     (zipmap [:tr-alkuosa :tr-alkuetaisyys]
                             (alku kohdeosa)))}
             (zipmap (iterate inc (+ 2 (count avaimet-ennen)))
                     (map #(get kohdeosat %) (rest avaimet-jalkeen)))))))

(defn kasittele-tallennettavat-kohteet! [oikeustarkistus-fn kohdetyyppi valmis-fn]
  (when (oikeustarkistus-fn)
    (fn [kohteet]
      (go (let [urakka-id (:id @nav/valittu-urakka)
                vuosi @u/valittu-urakan-vuosi
                [sopimus-id _] @u/valittu-sopimusnumero
                _ (log "[YLLÄPITOKOHTEET] Tallennetaan kohteet: " (pr-str kohteet))
                vastaus (<! (tallenna-yllapitokohteet!
                              urakka-id sopimus-id vuosi
                              (mapv #(assoc % :yllapitokohdetyotyyppi kohdetyyppi)
                                    kohteet)))]
            (if (k/virhe? vastaus)
              (viesti/nayta! "Kohteiden tallentaminen epännistui" :warning viesti/viestin-nayttoaika-keskipitka)
              (do (log "[YLLÄPITOKOHTEET] Kohteet tallennettu: " (pr-str vastaus))
                  (viesti/nayta! "Tallennus onnistui. Tarkista ja tallenna myös muokkaamiesi tieosoitteiden alikohteet."
                                 :success viesti/viestin-nayttoaika-keskipitka)
                  (valmis-fn vastaus))))))))

(defn yllapitokohteet-kartalle
  "Ylläpitokohde näytetään kartalla 'kohdeosina'.
   Ottaa vectorin ylläpitokohteita ja palauttaa ylläpitokohteiden kohdeosat valmiina näytettäväksi kartalle.
   Palautuneilla kohdeosilla on pääkohteen tiedot :yllapitokohde avaimen takana.

   yllapitokohteet  Vector ylläpitokohteita, joilla on mukana ylläpitokohteen kohdeosat (:kohdeosat avaimessa)
   lomakedata       Päällystys- tai paikkausilmoituksen lomakkeen tiedot"
  ([yllapitokohteet] (yllapitokohteet-kartalle yllapitokohteet nil))
  ([yllapitokohteet lomakedata]
   (let [id #(or (:paallystyskohde-id %)
                 (:paikkauskohde-id %)
                 (:yllapitokohde-id %))
         karttamuodossa (kartalla-esitettavaan-muotoon
                          yllapitokohteet
                          #(= (id lomakedata) (id %))
                          yllapitokohteet-domain/yllapitokohde-kartalle-xf)]
     karttamuodossa)))
