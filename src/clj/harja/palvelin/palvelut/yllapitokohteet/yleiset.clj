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
            [harja.domain.yllapitokohteet :as yllapitokohteet-domain]
            [harja.kyselyt.yllapitokohteet :as yllapitokohteet-q]
            [harja.domain.tierekisteri :as tr])
  (:use org.httpkit.fake))

(defn tarkista-urakkatyypin-mukainen-kirjoitusoikeus [db user urakka-id]
  (let [urakan-tyyppi (:tyyppi (first (urakat-q/hae-urakan-tyyppi db urakka-id)))]
    (case urakan-tyyppi
      "paallystys"
      (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-kohdeluettelo-paallystyskohteet user urakka-id)
      "paikkaus"
      (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-kohdeluettelo-paikkauskohteet user urakka-id)
      "tiemerkinta"
      (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-kohdeluettelo-paallystyskohteet user urakka-id))))

(defn tarkista-urakkatyypin-mukainen-lukuoikeus [db user urakka-id]
  (let [urakan-tyyppi (:tyyppi (first (urakat-q/hae-urakan-tyyppi db urakka-id)))]
    (case urakan-tyyppi
      "paallystys"
      (oikeudet/vaadi-lukuoikeus oikeudet/urakat-kohdeluettelo-paallystyskohteet user urakka-id)
      "paikkaus"
      (oikeudet/vaadi-lukuoikeus oikeudet/urakat-kohdeluettelo-paikkauskohteet user urakka-id)
      "tiemerkinta"
      (oikeudet/vaadi-lukuoikeus oikeudet/urakat-kohdeluettelo-paallystyskohteet user urakka-id))))

(defn vaadi-yllapitokohde-kuuluu-urakkaan [db urakka-id yllapitokohde-id]
  "Tarkistaa, että ylläpitokohde kuuluu annettuun urakkaan tai annettu urakka on merkitty
   suorittavaksi tiemerkintäurakakaksi. Jos kumpikaan ei ole totta, heittää poikkeuksen."
  (assert (and urakka-id yllapitokohde-id) "Ei voida suorittaa tarkastusta")
  (let [kohteen-urakka (:id (first (q/hae-yllapitokohteen-urakka-id db {:id yllapitokohde-id})))
        kohteen-suorittava-tiemerkintaurakka (:id (first (q/hae-yllapitokohteen-suorittava-tiemerkintaurakka-id
                                                           db
                                                           {:id yllapitokohde-id})))]
    (when (and (not= kohteen-urakka urakka-id)
               (not= kohteen-suorittava-tiemerkintaurakka urakka-id))
      (throw (SecurityException. (str "Ylläpitokohde " yllapitokohde-id " ei kuulu valittuun urakkaan "
                                      urakka-id " vaan urakkaan " kohteen-urakka
                                      ", eikä valittu urakka myöskään ole kohteen suorittava tiemerkintäurakka"))))))

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

(defn hae-urakan-yllapitokohteet [db user {:keys [urakka-id sopimus-id vuosi]}]
  (tarkista-urakkatyypin-mukainen-lukuoikeus db user urakka-id)
  (log/debug "Haetaan urakan ylläpitokohteet.")
  (jdbc/with-db-transaction [db db]
    (let [yllapitokohteet (into []
                                (comp
                                  (map #(assoc % :tila (yllapitokohteet-domain/yllapitokohteen-tarkka-tila %)))
                                  (map #(assoc % :tila-kartalla (yllapitokohteet-domain/yllapitokohteen-tila-kartalla %)))
                                  (map #(konv/string-polusta->keyword % [:paallystysilmoitus-tila]))
                                  (map #(konv/string-polusta->keyword % [:paikkausilmoitus-tila]))
                                  (map #(konv/string-polusta->keyword % [:yllapitokohdetyotyyppi]))
                                  (map #(konv/string-polusta->keyword % [:yllapitokohdetyyppi]))
                                  (map #(yllapitokohteet-q/liita-kohdeosat db % (:id %))))
                                (q/hae-urakan-sopimuksen-yllapitokohteet db {:urakka urakka-id
                                                                             :sopimus sopimus-id
                                                                             :vuosi vuosi}))
          osien-pituudet-tielle (laske-osien-pituudet db yllapitokohteet)
          yllapitokohteet (mapv #(assoc %
                                   :pituus
                                   (tr/laske-tien-pituus (osien-pituudet-tielle (:tr-numero %)) %))
                                yllapitokohteet)]
      yllapitokohteet)))