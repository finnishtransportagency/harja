(ns harja.tiedot.urakka.yllapitokohteet
  "Ylläpitokohteiden tiedot"
  (:require
    [harja.loki :refer [log tarkkaile!]]
    [cljs.core.async :refer [<!]]
    [harja.asiakas.kommunikaatio :as k]
    [harja.tiedot.urakka :as u]
    [harja.domain.yllapitokohde :as yllapitokohteet-domain]
    [harja.ui.kartta.esitettavat-asiat :refer [kartalla-esitettavaan-muotoon]]
    [harja.tiedot.navigaatio :as nav]
    [harja.ui.viesti :as viesti]
    [clojure.string :as str]
    [harja.tyokalut.local-storage :as local-storage])

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
            (or (str/blank? kohdenumero)
                (and (:kohdenumero %)
                     (str/starts-with? (str/lower-case (:kohdenumero %))
                                       (str/lower-case kohdenumero)))))
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

(defn tallenna-yllapitokohdeosat! [{:keys [urakka-id sopimus-id vuosi yllapitokohde-id osat osatyyppi]}]
  (k/post! :tallenna-yllapitokohdeosat {:urakka-id urakka-id
                                        :sopimus-id sopimus-id
                                        :vuosi vuosi
                                        :yllapitokohde-id yllapitokohde-id
                                        :osat osat
                                        :osatyyppi osatyyppi}))

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

(defn lisaa-uusi-pot2-alustarivi
  "Lisää uuden POT2-alustarivin annetussa indeksissä olevan kohteen perään (alapuolelle). Muuttaa kaikkien
  jälkeen tulevien osien avaimia yhdellä suuremmaksi."
  [kohdeosat key yllapitokohde]
  (let [rivi (get kohdeosat key)
        ;; Jos ennestään ei yhtään kohdeosaa täytetään ajorata ja kaista pääkohteelta
        rivi (if rivi
               rivi
               {:tr-numero (:tr-numero yllapitokohde)
                :tr-ajorata (:tr-ajorata yllapitokohde)
                :tr-kaista (:tr-kaista yllapitokohde)})
        avaimet-jalkeen (filter #(> % key) (keys kohdeosat))
        uusi-rivi {:tr-numero (:tr-numero rivi)
                   :tr-alkuosa nil
                   :tr-alkuetaisyys nil
                   :tr-loppuosa (:tr-loppuosa rivi)
                   :tr-loppuetaisyys (:tr-loppuetaisyys rivi)
                   :tr-ajorata (:tr-ajorata rivi)
                   :tr-kaista (:tr-kaista rivi)
                   :toimenpide nil}]
    (if (empty? kohdeosat)
      {key uusi-rivi}
      (-> kohdeosat
          (assoc-in [key :tr-loppuosa] nil)
          (assoc-in [key :tr-loppuetaisyys] nil)
          (assoc (inc key) uusi-rivi)
          (merge (zipmap (map inc avaimet-jalkeen)
                         (map #(get kohdeosat %) avaimet-jalkeen)))))))

(defn lisaa-uusi-kohdeosa
  "Lisää uuden kohteen annetussa indeksissä olevan kohteen perään (alapuolelle). Muuttaa kaikkien
  jälkeen tulevien osien avaimia yhdellä suuremmaksi."
  [kohdeosat key yllapitokohde]
  (let [rivi (get kohdeosat key)
        ;; Jos ennestään ei yhtään kohdeosaa täytetään ajorata ja kaista pääkohteelta
        rivi (if rivi
               rivi
               {:tr-numero (:tr-numero yllapitokohde)
                :tr-ajorata (:tr-ajorata yllapitokohde)
                :tr-kaista (:tr-kaista yllapitokohde)})
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
    (if (empty? kohdeosat)
      {key uusi-rivi}
      (-> kohdeosat
          (assoc-in [key :tr-loppuosa] nil)
          (assoc-in [key :tr-loppuetaisyys] nil)
          (assoc (inc key) uusi-rivi)
          (merge (zipmap (map inc avaimet-jalkeen)
                         (map #(get kohdeosat %) avaimet-jalkeen)))))))

(defn poista-kohdeosa
  "Poistaa valitun kohdeosan annetulla avaimella. Huolehtii siitä, että osat pysyvät järjestyksessä
   eikä väliin jää puuttumaan avaimia."
  [kohdeosat key]
  (let [kohdeosat (into (sorted-map)
                        (dissoc kohdeosat key))
        kohdeosat-uusilla-avaimilla (map-indexed (fn [index [vanha-avain rivi]]
                                                   [(inc index) rivi])
                                                 kohdeosat)
        tulos (reduce (fn [tulos [avain arvo]]
                        (assoc tulos avain arvo))
                      {}
                      kohdeosat-uusilla-avaimilla)]
    tulos))

(defn kasittele-tallennettavat-kohteet!
  ([kohteet kohdetyyppi onnistui-fn epaonnistui-fn] (kasittele-tallennettavat-kohteet! kohteet kohdetyyppi onnistui-fn epaonnistui-fn true true))
  ([kohteet kohdetyyppi onnistui-fn epaonnistui-fn nayta-onnistui? nayta-epaonnistui?]
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
               (if (= (:status vastaus) :ok)
                 (do
                   (when nayta-onnistui?
                     (viesti/nayta! "Tallennus onnistui. Tarkista myös muokkaamiesi tieosoitteiden alikohteet."
                                    :success viesti/viestin-nayttoaika-keskipitka))
                   (onnistui-fn (:yllapitokohteet vastaus)))
                 (do
                   (when nayta-epaonnistui?
                     (viesti/nayta! "Tallennus epäonnistui!"
                                    :danger viesti/viestin-nayttoaika-keskipitka))
                   (epaonnistui-fn vastaus)))))))))
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
