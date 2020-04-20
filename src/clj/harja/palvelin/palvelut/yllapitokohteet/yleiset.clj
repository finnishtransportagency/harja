(ns harja.palvelin.palvelut.yllapitokohteet.yleiset
  "Yleisiä apufunktioita ylläpitokohteiden palveluille"
  (:require [harja.kyselyt
             [konversio :as konv]
             [yllapitokohteet :as q]]
            [harja.palvelin.komponentit.http-palvelin
             :refer
             [julkaise-palvelu poista-palvelut]]
            [hiccup.core :refer [html]]
            [harja.tyokalut.functor :refer [fmap]]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.tieverkko :as tieverkko]
            [harja.domain.oikeudet :as oikeudet]
            [clojure.java.jdbc :as jdbc]
            [taoensso.timbre :as log]
            [clojure.core.async :refer [go]]
            [harja.domain.yllapitokohde :as yllapitokohde-domain]
            [harja.domain.tierekisteri :as tr]
            [harja.kyselyt.paallystys :as paallystys-q]
            [harja.palvelin.palvelut.tierekisteri-haku :as tr-haku]
            [harja.domain.yllapitokohde :as yllapitokohteet-domain]
            [harja.domain.paallystys-ja-paikkaus :as paallystys-ja-paikkaus]
            [harja.id :as id])
  (:use org.httpkit.fake))

(defn tarkista-urakkatyypin-mukainen-kirjoitusoikeus [db user urakka-id]
  (let [urakan-tyyppi (:tyyppi (first (urakat-q/hae-urakan-tyyppi db urakka-id)))]
    (case urakan-tyyppi
      "paallystys"
      (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-kohdeluettelo-paallystyskohteet user urakka-id)
      "paikkaus"
      (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-kohdeluettelo-paikkauskohteet user urakka-id)
      "tiemerkinta"
      (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-kohdeluettelo-paallystyskohteet user urakka-id)

      ;; muille urakkatyypeille ei tarkisteta
      nil)))

(defn tarkista-urakkatyypin-mukainen-lukuoikeus [db user urakka-id]
  (let [urakan-tyyppi (:tyyppi (first (urakat-q/hae-urakan-tyyppi db urakka-id)))]
    (case urakan-tyyppi
      "paallystys"
      (oikeudet/vaadi-lukuoikeus oikeudet/urakat-kohdeluettelo-paallystyskohteet user urakka-id)
      "paikkaus"
      (oikeudet/vaadi-lukuoikeus oikeudet/urakat-kohdeluettelo-paikkauskohteet user urakka-id)
      "tiemerkinta"
      (oikeudet/vaadi-lukuoikeus oikeudet/urakat-kohdeluettelo-paallystyskohteet user urakka-id)

      ;; muille urakkatyypeille ei tarkisteta
      nil)))

(defn vaadi-yllapitokohde-kuuluu-urakkaan-tai-on-suoritettavana-tiemerkintaurakassa [db urakka-id yllapitokohde-id]
  "Tarkistaa, että ylläpitokohde kuuluu annettuun urakkaan tai annettu urakka on merkitty
   suorittavaksi tiemerkintäurakakaksi. Jos kumpikaan ei ole totta, heittää poikkeuksen."
  (assert urakka-id "Urakka-id puuttuu")
  (when (id/id-olemassa? yllapitokohde-id)
    (let [kohteen-urakka (:id (first (q/hae-yllapitokohteen-urakka-id db {:id yllapitokohde-id})))
          kohteen-suorittava-tiemerkintaurakka (:id (first (q/hae-yllapitokohteen-suorittava-tiemerkintaurakka-id
                                                             db
                                                             {:id yllapitokohde-id})))]
      (when (and (not= kohteen-urakka urakka-id)
                 (not= kohteen-suorittava-tiemerkintaurakka urakka-id))
        (throw (SecurityException. (str "Ylläpitokohde " yllapitokohde-id " ei kuulu valittuun urakkaan "
                                        urakka-id " vaan urakkaan " kohteen-urakka
                                        ", eikä valittu urakka myöskään ole kohteen suorittava tiemerkintäurakka")))))))

(defn vaadi-yllapitokohde-osoitettu-tiemerkintaurakkaan [db urakka-id yllapitokohde-id]
  "Tarkistaa, että ylläpitokohde on osoitettu annetulle tiemerkintäurakka-id:lle suoritettavaksi.
   Jos ei ole, heittää poikkeuksen."
  (assert urakka-id "Urakka-id puuttuu")
  (when (id/id-olemassa? yllapitokohde-id)
    (let [kohteen-suorittava-tiemerkintaurakka (:id (first (q/hae-yllapitokohteen-suorittava-tiemerkintaurakka-id
                                                             db
                                                             {:id yllapitokohde-id})))]
      (when (not= kohteen-suorittava-tiemerkintaurakka urakka-id)
        (throw (SecurityException. (str "Ylläpitokohde " yllapitokohde-id " ei ole urakan "
                                        urakka-id " suoritettavana tiemerkintään, vaan urakan "
                                        kohteen-suorittava-tiemerkintaurakka)))))))

(defn vaadi-yllapitokohde-kuuluu-urakkaan
  [db urakka-id yllapitokohde-id]
  "Tarkistaa, että ylläpitokohde kuuluu annettuun urakkaan. Jos ei kuulu, heittää poikkeuksen."
  (assert urakka-id "Urakka-id puuttuu")
  (when (id/id-olemassa? yllapitokohde-id)
    (let [kohteen-urakka (:id (first (q/hae-yllapitokohteen-urakka-id db {:id yllapitokohde-id})))]
      (when (not= kohteen-urakka urakka-id)
        (throw (SecurityException. (str "Ylläpitokohde " yllapitokohde-id " ei kuulu valittuun urakkaan "
                                        urakka-id " vaan urakkaan " kohteen-urakka)))))))

(defn vaadi-paikkauskohde-kuuluu-urakkaan
  [db urakka-id paikkauskohde-id]
  "Tarkistaa, että paikkauskohde kuuluu annettuun urakkaan. Jos ei kuulu, heittää poikkeuksen."
  (assert urakka-id "Urakka-id puuttuu")
  (when (id/id-olemassa? paikkauskohde-id)
    (let [kohteen-urakka (:id (first (q/hae-paikkauskohteen-urakka-id db {:id paikkauskohde-id})))]
      (when (not= kohteen-urakka urakka-id)
        (throw (SecurityException. (str "Paikkauskohde " paikkauskohde-id " ei kuulu valittuun urakkaan "
                                        urakka-id " vaan urakkaan " kohteen-urakka)))))))

(defn lukuoikeus-paallystys-tai-tiemerkintaurakan-aikatauluun? [db user yllapitokohde-id]
  (assert yllapitokohde-id "Ylläpitokohde-id puuttuu")
  (let [kohteen-urakka-id (:id (first (q/hae-yllapitokohteen-urakka-id db {:id yllapitokohde-id})))
        kohteen-suorittava-tiemerkintaurakka-id (:id (first (q/hae-yllapitokohteen-suorittava-tiemerkintaurakka-id
                                                              db
                                                              {:id yllapitokohde-id})))]
    (boolean (or (oikeudet/voi-lukea? oikeudet/urakat-aikataulu kohteen-urakka-id user)
                 (oikeudet/voi-lukea? oikeudet/urakat-aikataulu kohteen-suorittava-tiemerkintaurakka-id user)))))

(defn laske-osien-pituudet
  "Hakee tieverkosta osien pituudet tielle. Palauttaa pituuden metreina."
  [db yllapitokohteet]
  (fmap
    (fn [osat]
      (let [tie (:tr-numero (first osat))
            osat (into #{}
                       (comp (mapcat (juxt :tr-alkuosa :tr-loppuosa))
                             (remove nil?))
                       osat)
            min-osa (reduce min 1 osat)
            max-osa (reduce max 1 osat)]
        (into {}
              (map (juxt :osa :pituus))
              (tieverkko/hae-osien-pituudet db tie min-osa max-osa))))
    (group-by :tr-numero yllapitokohteet)))

(defn- yllapitokohde-sisaltaa-urakassa-tehtyja-kirjauksia?
  "Palauttaa true tai false sen mukaan onko ylläpitokohteeseen liitetty kirjauksia annetussa
   urakassa (laatupoikkeamia, tarkastuksia, toteumia...)"
  [db yllapitokohde-id urakka-id]
  (let [kirjauksia? (:kirjauksia (first (q/yllapitokohde-sisaltaa-kirjauksia-urakassa
                                          db {:yllapitokohde_id yllapitokohde-id
                                              :urakka_id urakka-id})))]
    kirjauksia?))

(defn- yllapitokohde-sisaltaa-tiemerkintaaikataulun?
  [db yllapitokohde-id]
  (let [aikataulu (first (q/hae-yllapitokohteen-tiemerkintaaikataulu
                           db {:id yllapitokohde-id}))
        aikatauluarvot (vals aikataulu)
        ajalliset-aikatauluarvot (remove nil? aikatauluarvot)]
    (not (empty? ajalliset-aikatauluarvot))))

(defn yllapitokohteen-voi-poistaa?
  [db yllapitokohde-id]
  (let [saa-poistaa? (:saa-poistaa (first (q/yllapitokohteen-saa-poistaa db {:id yllapitokohde-id})))]
    saa-poistaa?))

(defn yllapitokohteen-suorittavan-tiemerkintaurakan-voi-vaihtaa?
  [db yllapitokohde-id tiemerkintaurakka-id]
  (if tiemerkintaurakka-id
    (and (not (yllapitokohde-sisaltaa-urakassa-tehtyja-kirjauksia? db yllapitokohde-id tiemerkintaurakka-id))
         (not (yllapitokohde-sisaltaa-tiemerkintaaikataulun? db yllapitokohde-id)))
    true))

(def maaramuutoksen-tyon-tyyppi->kantaenum
  {:ajoradan-paallyste "ajoradan_paallyste"
   :pienaluetyot "pienaluetyot"
   :tasaukset "tasaukset"
   :jyrsinnat "jyrsinnat"
   :muut "muut"})

(def maaramuutoksen-tyon-tyyppi->keyword
  {"ajoradan_paallyste" :ajoradan-paallyste
   "pienaluetyot" :pienaluetyot
   "tasaukset" :tasaukset
   "jyrsinnat" :jyrsinnat
   "muut" :muut})

(def maaramuutos-xf
  (comp
    (map #(assoc % :tyyppi (maaramuutoksen-tyon-tyyppi->keyword (:tyyppi %))))
    (map #(konv/string-polusta->keyword % [:tyyppi]))))

(defn hae-yllapitokohteen-maaramuutokset
  [db {:keys [yllapitokohde-id urakka-id]}]
  (log/debug "Aloitetaan määrämuutoksien haku kohteelle " yllapitokohde-id)
  (let [maaramuutokset (into []
                             maaramuutos-xf
                             (paallystys-q/hae-yllapitokohteen-maaramuutokset db {:id yllapitokohde-id
                                                                                  :urakka urakka-id}))]
    maaramuutokset))

(defn hae-yllapitokohteiden-maaramuutokset
  [db yllapitokohde-idt]
  (log/debug "Aloitetaan määrämuutoksien haku kohteille: " yllapitokohde-idt)
  (let [maaramuutokset (into []
                             maaramuutos-xf
                             (paallystys-q/hae-yllapitokohteiden-maaramuutokset
                               db {:idt yllapitokohde-idt}))]
    maaramuutokset))

(defn liita-yllapitokohteisiin-maaramuutokset
  "Liittää ylläpitokohteisiin määrämuutoksien kokonaissumman"
  [db yllapitokohteet]
  (let [yllapitokohteiden-maaramuutokset (hae-yllapitokohteiden-maaramuutokset
                                           db (map :id yllapitokohteet))
        vastaus (mapv (fn [yllapitokohde]
                        (if (= (:yllapitokohdetyotyyppi yllapitokohde) :paallystys)
                          (let [kohteen-maaramuutokset (filter #(= (:yllapitokohde-id %) (:id yllapitokohde))
                                                               yllapitokohteiden-maaramuutokset)
                                summatut-maaramuutokset (paallystys-ja-paikkaus/summaa-maaramuutokset kohteen-maaramuutokset)
                                maaramuutokset (:tulos summatut-maaramuutokset)
                                maaramuutos-ennustettu? (:ennustettu? summatut-maaramuutokset)]
                            (assoc yllapitokohde :maaramuutokset maaramuutokset
                                                 :maaramuutokset-ennustettu? maaramuutos-ennustettu?))
                          yllapitokohde))
                      yllapitokohteet)]
    vastaus))

(def urakan-yllapitokohde-xf
  (comp
    yllapitokohteet-domain/yllapitoluokka-xf
    (map #(assoc % :tila (yllapitokohde-domain/yllapitokohteen-tarkka-tila %)))
    (map #(assoc % :vuodet (set (konv/pgarray->vector (:vuodet %)))))
    (map #(konv/string-polusta->keyword % [:paallystysilmoitus-tila]))
    (map #(konv/string-polusta->keyword % [:paikkausilmoitus-tila]))
    (map #(konv/string-polusta->keyword % [:yllapitokohdetyotyyppi]))
    (map #(konv/string-polusta->keyword % [:yllapitokohdetyyppi]))))

(defn- hae-urakan-yllapitokohteet* [db {:keys [urakka-id sopimus-id vuosi]}]
  (let [yllapitokohteet (into []
                              urakan-yllapitokohde-xf
                              (q/hae-urakan-sopimuksen-yllapitokohteet db {:urakka urakka-id
                                                                           :sopimus sopimus-id
                                                                           :vuosi vuosi}))
        idt (map :id yllapitokohteet)
        yllapitokohteet (q/liita-kohdeosat-kohteisiin db yllapitokohteet :id)
        osien-pituudet-tielle (laske-osien-pituudet db yllapitokohteet)
        ei-voi-poistaa (into #{}
                             (map :yllapitokohde)
                             (q/yllapitokohteet-joille-linkityksia db {:idt idt}))
        yllapitokohteiden-aikataulu-muokattu (q/hae-yllapitokohteiden-aikataulun-muokkaus-aika db {:idt idt})
        etsi-yllapitokohde (fn [id]
                             (some #(when (= id (:yllapitokohde %)) %)
                                   yllapitokohteiden-aikataulu-muokattu))
        yllapitokohteet (->> yllapitokohteet
                             (map #(assoc % :pituus
                                            (tr/laske-tien-pituus (osien-pituudet-tielle (:tr-numero %)) %)
                                            :yllapitokohteen-voi-poistaa?
                                            (not (ei-voi-poistaa (:id %)))
                                            :aikataulu-muokattu
                                            (:muokattu (etsi-yllapitokohde (:id %))))))]
    (vec yllapitokohteet)))

(defn hae-urakan-yllapitokohteet [db {:keys [urakka-id sopimus-id vuosi]}]
  (log/debug "Haetaan urakan ylläpitokohteet.")
  (let [yllapitokohteet (hae-urakan-yllapitokohteet* db {:urakka-id urakka-id
                                                         :sopimus-id sopimus-id
                                                         :vuosi vuosi})
        yllapitokohteet (liita-yllapitokohteisiin-maaramuutokset db yllapitokohteet)]
    (vec yllapitokohteet)))

(defn lisaa-yllapitokohteelle-pituus [db {:keys [tr-numero tr-alkuosa tr-loppuosa] :as kohde}]
  (let [osien-pituudet (tr-haku/hae-osien-pituudet db {:tie tr-numero
                                                       :aosa tr-alkuosa
                                                       :losa tr-loppuosa})
        pituus (tr/laske-tien-pituus osien-pituudet kohde)]
    (assoc kohde :pituus pituus)))

(defn paivita-yllapitourakan-geometria [db urakka-id]
  (log/info "Päivitetään urakan " urakka-id " geometriat.")
  (q/paivita-paallystys-tai-paikkausurakan-geometria db {:urakka urakka-id}))

(defn paallekkaiset-kohdeosat-saman-vuoden-osien-kanssa
  "Tarkistaa, etteivät annetut kohdeosat mene päällekkäin muiden saman vuoden kohdeosien kanssa.
   Palauttaa kokoelman virheitä (string), mikäli niitä ilmenee."
  [db yllapitokohde-id vuosi kohdeosat]
  (let [tiet (distinct (map :tr-numero kohdeosat))
        teiden-kohdeosat (group-by (juxt :tr-numero :tr-ajorata :tr-kaista)
                                   (q/hae-yhden-vuoden-kohdeosat-teille db {:yllapitokohdeid yllapitokohde-id
                                                                            :vuosi vuosi
                                                                            :tiet tiet}))
        virheet (map (fn [{tallennettava-id :id
                           :keys [tr-numero tr-ajorata tr-kaista]
                           :as tallennettava-kohde}]
                       (let [kohteet-samalta-sijainnilta (get teiden-kohdeosat [tr-numero tr-ajorata tr-kaista])]
                         (keep (fn [{olemassa-oleva-id :id
                                     :keys [urakan-nimi
                                            paakohteen-nimi
                                            yllapitokohteen-nimi]
                                     :as olemassaoleva-kohde}]
                                 (when (and (not= olemassa-oleva-id tallennettava-id)
                                            (tr/kohdeosat-paalekkain? olemassaoleva-kohde tallennettava-kohde))
                                   (format "Kohde: '%s' menee päällekkäin urakan: '%s' kohteen: '%s' kohdeosan: '%s' kanssa."
                                           (:nimi tallennettava-kohde)
                                           urakan-nimi
                                           paakohteen-nimi
                                           yllapitokohteen-nimi)))
                               kohteet-samalta-sijainnilta)))
                     kohdeosat)]
    (apply concat virheet)))


