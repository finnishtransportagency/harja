(ns harja.palvelin.integraatiot.api.tyokalut.validointi
  "Yleisiä API-kutsuihin liittyviä apufunktioita"
  (:require
    [taoensso.timbre :as log]
    [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]
    [harja.kyselyt.urakat :as q-urakat]
    [harja.kyselyt.sopimukset :as q-sopimukset]
    [harja.kyselyt.yllapitokohteet :as q-yllapitokohteet]
    [harja.kyselyt.tieverkko :as q-tieverkko]
    [harja.kyselyt.kayttajat :as kayttajat-q]
    [harja.kyselyt.tietyomaat :as q-tietyomaat]
    [harja.domain.roolit :as roolit]
    [harja.domain.oikeudet :as oikeudet]
    [harja.domain.yllapitokohde :as kohteet]
    [harja.kyselyt.paallystys :as paallystys-q]
    [harja.domain.tierekisteri :as tierekisteri]
    [harja.geo :as geo]
    [cheshire.core :as cheshire]
    [harja.palvelin.palvelut.yllapitokohteet.yleiset :as yy]
    [clojure.string :as str])
  (:use [slingshot.slingshot :only [throw+ try+]]))

(declare tarkista-muut-alikohteet)

(defn tarkista-urakka [db urakka-id]
  (log/debug "Validoidaan urakkaa id:llä" urakka-id)
  (when (not (q-urakat/onko-olemassa? db urakka-id))
    (do
      (let [viesti (format "Urakkaa id:llä %s ei löydy." urakka-id)]
        (log/warn viesti)
        (throw+ {:type virheet/+viallinen-kutsu+
                 :virheet [{:koodi virheet/+tuntematon-urakka-koodi+ :viesti viesti}]})))))

(defn tarkista-sopimus [db urakka-id sopimus-id]
  (log/debug (format "Validoidaan urakan id: %s sopimusta id: %s" urakka-id sopimus-id)
             (when (not (q-sopimukset/onko-olemassa? db urakka-id sopimus-id))
               (do
                 (let [viesti (format "Urakalle id: %s ei löydy sopimusta id: %s." urakka-id sopimus-id)]
                   (log/warn viesti)
                   (throw+ {:type virheet/+viallinen-kutsu+
                            :virheet [{:koodi virheet/+tuntematon-sopimus-koodi+ :viesti viesti}]}))))))

(defn viivageometria-annettu [havainto]
  (when-not (get-in havainto [:havainto :sijainti :viivageometria])
    (throw+ {:type virheet/+viallinen-kutsu+
             :virheet [{:koodi virheet/+sisainen-kasittelyvirhe-koodi+ :viesti "Sanomasta puuttuu viivageometria"}]})))

(defn tarkista-koordinaattien-jarjestys [koordinaatit]
  (let [[x y] (geo/xy koordinaatit)]
    (when (> x y)
      (throw+ {:type virheet/+viallinen-kutsu+
               :virheet [{:koodi virheet/+sisainen-kasittelyvirhe-koodi+ :viesti "Koordinaattien järjestys väärä"}]}))))

(defn tarkista-kayttajan-oikeudet-urakkaan [db urakka-id kayttaja]
  (oikeudet/merkitse-oikeustarkistus-tehdyksi!)
  (when (and (not (kayttajat-q/onko-kayttaja-urakan-organisaatiossa? db urakka-id (:id kayttaja)))
             (not (kayttajat-q/onko-kayttajalla-lisaoikeus-urakkaan? db urakka-id (:id kayttaja))))
    (throw+ {:type virheet/+viallinen-kutsu+
             :virheet [{:koodi virheet/+kayttajalla-puutteelliset-oikeudet+
                        :viesti (str "Käyttäjällä: " (:kayttajanimi kayttaja) " ei ole oikeuksia urakkaan: "
                                     urakka-id)}]})))

(defn tarkista-onko-kayttaja-organisaatiossa [db ytunnus kayttaja]
  (oikeudet/merkitse-oikeustarkistus-tehdyksi!)
  (when-not (kayttajat-q/onko-kayttaja-organisaatiossa? db ytunnus (:id kayttaja))
    (throw+ {:type virheet/+viallinen-kutsu+
             :virheet [{:koodi virheet/+kayttajalla-puutteelliset-oikeudet+
                        :viesti (str "Käyttäjällä: " (:kayttajanimi kayttaja) " ei ole oikeuksia organisaatioon: " ytunnus)}]})))

(defn tarkista-onko-kayttaja-organisaation-jarjestelma [db ytunnus kayttaja]
  (tarkista-onko-kayttaja-organisaatiossa db ytunnus kayttaja)
  (when (not (:jarjestelma kayttaja))
    (throw+ {:type virheet/+viallinen-kutsu+
             :virheet [{:koodi virheet/+tuntematon-kayttaja-koodi+
                        :viesti (str "Käyttäjä " (:kayttajanimi kayttaja) " ei ole järjestelmä")}]})))

(defn tarkista-urakka-ja-kayttaja [db urakka-id kayttaja]
  (oikeudet/merkitse-oikeustarkistus-tehdyksi!)
  (tarkista-urakka db urakka-id)
  (tarkista-kayttajan-oikeudet-urakkaan db urakka-id kayttaja))

(defn tarkista-rooli [kayttaja rooli]
  (oikeudet/merkitse-oikeustarkistus-tehdyksi!)
  (when-not (roolit/roolissa? kayttaja rooli)
    (throw+ {:type virheet/+viallinen-kutsu+
             :virheet [{:koodi virheet/+tuntematon-kayttaja-koodi+
                        :viesti (str "Käyttäjällä ei oikeutta resurssiin.")}]})))

(defn tarkista-urakka-sopimus-ja-kayttaja [db urakka-id sopimus-id kayttaja]
  (tarkista-urakka db urakka-id)
  (tarkista-sopimus db urakka-id sopimus-id)
  (tarkista-kayttajan-oikeudet-urakkaan db urakka-id kayttaja))

(defn tarkista-oikeudet-urakan-paivystajatietoihin [db urakka-id kayttaja]
  (oikeudet/merkitse-oikeustarkistus-tehdyksi!)
  (when-not (or (roolit/roolissa? kayttaja roolit/liikennepaivystaja)
                (kayttajat-q/onko-kayttaja-urakan-organisaatiossa? db urakka-id (:id kayttaja))
                (kayttajat-q/onko-kayttajalla-lisaoikeus-urakkaan? db urakka-id (:id kayttaja)))

    (throw+ {:type virheet/+viallinen-kutsu+
             :virheet [{:koodi virheet/+kayttajalla-puutteelliset-oikeudet+
                        :viesti (format "Käyttäjällä: %s ei ole oikeuksia urakan: %s päivystäjätietoihin."
                                        (:kayttajanimi kayttaja)
                                        urakka-id)}]})))



(defn sijainneissa-virheita? [db tienumero sijainnit]
  (not-every? #(q-tieverkko/onko-tierekisteriosoite-validi?
                 db
                 tienumero
                 (:aosa %)
                 (:aet %)
                 (:losa %)
                 (:let %))
              sijainnit))

(defn validoi-kohteiden-sijainnit-tieverkolla [db tienumero kohteen-sijainti alikohteet]
  (let [sijainnit (conj (mapv :sijainti alikohteet) kohteen-sijainti)]
    (when
      (sijainneissa-virheita? db tienumero sijainnit)
      (virheet/heita-poikkeus
        virheet/+viallinen-kutsu+
        [{:koodi :viallisia-tieosia
          :viesti "Päällystysilmoitus sisältää kohteen tai alikohteita, joita ei löydy tieverkolta"}]))))

(defn tarkista-paallystysilmoituksen-kohde-ja-alikohteet
  "Tarkistaa, että kohteen ja alikohteiden arvot on annettu oikein ja osoitteet löytyvät Harjan tieverkolta"
  [db kohde-id kohteen-tienumero kohteen-sijainti alikohteet]
  (try+
    (kohteet/tarkista-kohteen-ja-alikohteiden-sijannit kohde-id kohteen-sijainti alikohteet)
    (catch [:type kohteet/+kohteissa-viallisia-sijainteja+] {:keys [virheet]}
      (virheet/heita-poikkeus virheet/+viallinen-kutsu+ virheet)))
  (validoi-kohteiden-sijainnit-tieverkolla db kohteen-tienumero kohteen-sijainti alikohteet))

(defn tarkista-alustatoimenpiteet [db kohde-id kohteen-tienumero kohteen-sijainti alustatoimenpiteet]
  (try+
    (kohteet/tarkista-alustatoimenpiteiden-sijainnit kohde-id kohteen-sijainti alustatoimenpiteet)
    (catch [:type kohteet/+kohteissa-viallisia-sijainteja+] {:keys [virheet]}
      (virheet/heita-poikkeus virheet/+viallinen-kutsu+ virheet)))
  (when (not (empty? alustatoimenpiteet))
    (let [sijainnit (mapv :sijainti alustatoimenpiteet)]
      (when
        (sijainneissa-virheita? db kohteen-tienumero sijainnit)
        (virheet/heita-poikkeus
          virheet/+viallinen-kutsu+
          [{:koodi :viallisia-tieosia
            :viesti "Alustatoimenpiteet sisältävät sijainteja, joita ei löydy tieverkolta"}])))))

(defn tarkista-paallystysilmoitus [db kohde-id kohteen-tienumero kohteen-sijainti alikohteet alustatoimenpiteet]
  (let [paakohteen-sisalla? #(= kohteen-tienumero (or (get-in % [:sijainti :tie]) (get-in % [:sijainti :numero])))
        paakohteen-alikohteet (filter paakohteen-sisalla? alikohteet)
        muut-alikohteet (filter (comp not paakohteen-sisalla?) alikohteet)]
    (tarkista-paallystysilmoituksen-kohde-ja-alikohteet db kohde-id kohteen-tienumero kohteen-sijainti paakohteen-alikohteet)
    (tarkista-muut-alikohteet db muut-alikohteet)
    (tarkista-alustatoimenpiteet db kohde-id kohteen-tienumero kohteen-sijainti alustatoimenpiteet)))

(defn tarkista-tietyomaa [db id jarjestelma]
  (when (not (q-tietyomaat/onko-olemassa? db {:id id :jarjestelma jarjestelma}))
    (do
      (let [viesti (format "Suljettua tieosuutta (id: %s) ei löydy" id)]
        (log/warn viesti)
        (virheet/heita-poikkeus
          virheet/+viallinen-kutsu+
          [{:koodi virheet/+tuntematon-yllapitokohde+ :viesti viesti}])))))

(defn tarkista-saako-kohteen-paivittaa [db kohde-id]
  (when (paallystys-q/onko-olemassa-paallystysilmoitus? db kohde-id)
    (do
      (let [viesti (format "Kohteelle (id: %s) on jo kirjattu päällystysilmoitus. Päivitys ei ole sallittu" kohde-id)]
        (log/warn viesti)
        (virheet/heita-poikkeus
          virheet/+viallinen-kutsu+
          [{:koodi virheet/+lukittu-yllapitokohde+ :viesti viesti}])))))

(defn tarkista-yllapitokohde-kuuluu-urakkatyypin-mukaiseen-urakkaan
  "Tarkistaa, että ylläpitokohde kuuluu annettuun urakkaan suoraan tai on merkitty suoritettavaksi
   tiemerkintäurakaksi."
  [db urakka-id urakan-tyyppi kohde-id]
  (let [urakan-kohteet (case urakan-tyyppi
                         :paallystys
                         (q-yllapitokohteet/hae-urakkaan-kuuluvat-yllapitokohteet db {:urakka urakka-id})
                         :tiemerkinta
                         (q-yllapitokohteet/hae-urakkaan-liittyvat-tiemerkintakohteet db {:urakka urakka-id}))]
    (when-not (some #(= kohde-id %) (map :id urakan-kohteet))
      (virheet/heita-poikkeus virheet/+viallinen-kutsu+
                              {:koodi virheet/+urakkaan-kuulumaton-yllapitokohde+
                               :viesti "Ylläpitokohde ei kuulu urakkaan."}))))

(defn tarkista-yllapitokohde-kuuluu-urakkaan [db urakka-id kohde-id]
  (log/debug (format "Validoidaan urakan (id: %s) kohdetta (id: %s)" urakka-id kohde-id))
  (when (not (q-yllapitokohteet/onko-olemassa-urakalla? db {:urakka urakka-id :kohde kohde-id}))
    (do
      (let [viesti (format "Urakalla (id: %s) ei ole kohdetta (id: %s)." urakka-id kohde-id)]
        (log/warn viesti)
        (virheet/heita-poikkeus
          virheet/+viallinen-kutsu+
          [{:koodi virheet/+tuntematon-yllapitokohde+ :viesti viesti}])))))

(defn tarkista-onko-liikenneviraston-jarjestelma [db kayttaja]
  (when-not (kayttajat-q/liikenneviraston-jarjestelma? db (:kayttajanimi kayttaja))
    (log/error (format "Kayttajatunnus %s ei ole Väylän järjestelmä eikä sillä ole oikeutta kutsuttuun resurssiin."
                       (:kayttajatunnus kayttaja)))
    (throw+ {:type virheet/+kayttajalla-puutteelliset-oikeudet+
             :virheet [{:koodi virheet/+kayttajalla-puutteelliset-oikeudet+
                        :viesti "Käyttäjällä ei resurssiin."}]})))

(defn tarkista-leikkaavatko-alikohteet-toisiaan
  [alikohteet]
  (let [paallekkain? (fn [ensimmainen toinen]
                       (let [ensimmainen-sijainti (tierekisteri/tr-alkuiseksi (:sijainti ensimmainen))
                             toinen-sijainti (tierekisteri/tr-alkuiseksi (:sijainti toinen))]
                         (when (tierekisteri/kohdeosat-paalekkain? ensimmainen-sijainti toinen-sijainti)
                           {:koodi virheet/+virheellinen-sijainti+
                            :viesti (format "Alikohteiden: %s (tunniste: %s) ja: %s (tunniste: %s) osoitteet leikkaavat toisiaan."
                                            (:nimi ensimmainen)
                                            (get-in ensimmainen [:tunniste :id])
                                            (:nimi toinen)
                                            (get-in toinen [:tunniste :id]))})))]
    (apply concat (for [[x i] (zipmap alikohteet (range (count alikohteet)))]
                    (keep (partial paallekkain? x)
                          (concat (take i alikohteet) (drop (+ 1 i) alikohteet)))))))

(defn tarkista-ovatko-tierekisterosoitteet-validit [db alikohteet]
  (let [validi? (fn [{:keys [sijainti nimi tunniste tunnus]}]
                  (let [numero (:numero sijainti)
                        aosa (:aosa sijainti)
                        aet (:aet sijainti)
                        losa (:losa sijainti)
                        loppuet (:let sijainti)
                        tunniste (or tunnus (:id tunniste))]
                    (if (q-tieverkko/onko-tierekisteriosoite-validi? db numero aosa aet losa loppuet)
                      (when (not (q-tieverkko/ovatko-tierekisteriosoitteen-etaisyydet-validit? db numero aosa aet losa loppuet))
                        {:koodi virheet/+virheellinen-sijainti+
                         :viesti (format "Kohteen: %s (tunniste: %s) osoite ei ole validi. Etäisyydet ovat liian pitkiä."
                                         nimi
                                         tunniste)})
                      {:koodi virheet/+virheellinen-sijainti+
                       :viesti (format "Kohteen: %s (tunniste: %s) osoite ei ole validi. Tietä tai osaa ei löydy."
                                       nimi
                                       tunniste)})))]
    (filter (comp not nil?) (map validi? alikohteet))))

(defn tarkista-muut-alikohteet [db muut-alikohteet]
  (let [virheet (concat
                  (tarkista-leikkaavatko-alikohteet-toisiaan muut-alikohteet)
                  (tarkista-ovatko-tierekisterosoitteet-validit db muut-alikohteet))]
    (when (not (empty? virheet))
      (virheet/heita-poikkeus virheet/+viallinen-kutsu+ virheet))))

(defn tarkista-alikohteiden-paallekkaisyys [db kohde-id kohteen-vuodet alikohteet]
  (doseq [vuosi kohteen-vuodet]
    (let [paallekkaiset-osat (yy/paallekkaiset-kohdeosat-saman-vuoden-osien-kanssa
                               db kohde-id vuosi (map #(-> {:nimi (:nimi %)
                                                            :tr-numero (get-in % [:sijainti :numero])
                                                            :tr-ajorata (get-in % [:sijainti :ajr])
                                                            :tr-kaista (get-in % [:sijainti :kaista])
                                                            :tr-alkuosa (get-in % [:sijainti :aosa])
                                                            :tr-alkuetaisyys (get-in % [:sijainti :aet])
                                                            :tr-loppuosa (get-in % [:sijainti :losa])
                                                            :tr-loppuetaisyys (get-in % [:sijainti :let])})
                                                      alikohteet))]
      (when (not (empty? paallekkaiset-osat))
        (virheet/heita-poikkeus
          virheet/+viallinen-kutsu+
          {:koodi virheet/+viallinen-kutsu+
           :viesti (str/join ", " paallekkaiset-osat)})))))

(defn tarkista-urakkatyyppi [urakkatyyppi]
  (when urakkatyyppi
    (let [urakkatyypit #{"hoito"
                         "paallystys"
                         "paikkaus"
                         "tiemerkinta"
                         "valaistus"
                         "siltakorjaus"
                         "tekniset-laitteet"
                         "vesivayla-hoito"
                         "vesivayla-ruoppaus"
                         "vesivayla-turvalaitteiden-korjaus"
                         "vesivayla-kanavien-hoito"
                         "vesivayla-kanavien-korjaus"}]
      (when (not (contains? urakkatyypit urakkatyyppi))
        (virheet/heita-viallinen-apikutsu-poikkeus
          {:koodi virheet/+puutteelliset-parametrit+
           :viesti (format "Tuntematon urakkatyyppi: %s" urakkatyyppi)})))))