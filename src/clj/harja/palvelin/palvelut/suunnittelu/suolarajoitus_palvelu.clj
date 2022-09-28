(ns harja.palvelin.palvelut.suunnittelu.suolarajoitus-palvelu
  (:require [clojure.java.jdbc :as jdbc]
            [com.stuartsierra.component :as component]
            [cheshire.core :as cheshire]
            [harja.tyokalut.yleiset :as yleiset-tyokalut]
            [harja.kyselyt.suolarajoitus-kyselyt :as suolarajoitus-kyselyt]
            [harja.kyselyt.tieverkko :as tieverkko-kyselyt]
            [harja.kyselyt.urakat :as urakat-kyselyt]
            [harja.kyselyt.materiaalit :as materiaalit-kyselyt]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut transit-vastaus]]
            [harja.kyselyt.konversio :as konv]
            [harja.domain.oikeudet :as oikeudet]
            [taoensso.timbre :as log]
            [clj-time.coerce :as c]
            [harja.pvm :as pvm]
            [harja.tyokalut.big :as big]))

(defn tierekisteri-muokattu? [uusi-rajoitusalue vanha-rajoitusalue]
  (if (and
        (= (:tie uusi-rajoitusalue) (:tie vanha-rajoitusalue))
        (= (:aosa uusi-rajoitusalue) (:aosa vanha-rajoitusalue))
        (= (:aet uusi-rajoitusalue) (:aet vanha-rajoitusalue))
        (= (:losa uusi-rajoitusalue) (:losa vanha-rajoitusalue))
        (= (:let uusi-rajoitusalue) (:let vanha-rajoitusalue)))
    false
    true))

(defn pituuden-laskennan-data-validi?
  [{:keys [tie aosa losa aet let] :as suolarajoitus}]
  (and
    (pos-int? tie)
    (int? aosa)
    (int? losa)
    (int? aet)
    (int? let)))

(defn- tierekisterin-tiedot [db {:keys [urakka-id hoitokauden-alkuvuosi] :as suolarajoitus}]
  (log/debug "tierekisterin-tiedot :: suolarajoitus: " suolarajoitus)
  (let [validaatiovirheet nil
        tierekisteri-valid? (pituuden-laskennan-data-validi? suolarajoitus)
        validaatiovirheet (if-not tierekisteri-valid?
                            (str validaatiovirheet "Tierekisteriosoitteessa virhe. \n")
                            validaatiovirheet)
        tarkistettava-suolarajoitus {:tie (:tie suolarajoitus)
                                     :aosa (:aosa suolarajoitus)
                                     :aet (:aet suolarajoitus)
                                     :losa (:losa suolarajoitus)
                                     :let (:let suolarajoitus)
                                     :urakka-id urakka-id
                                     :hoitokauden-alkuvuosi hoitokauden-alkuvuosi
                                     :rajoitusalue-id (:rajoitusalue-id suolarajoitus)}
        paallekaiset (when tierekisteri-valid?
                       (suolarajoitus-kyselyt/onko-tierekisteriosoite-paallekainen db tarkistettava-suolarajoitus))
        validaatiovirheet (if-not (empty? paallekaiset)
                            (str validaatiovirheet " Tierekisteriosoitteessa on jo rajoitus. ")
                            validaatiovirheet)

        ;; Pilkotaan tierekisteri osiin tien osien mukaan
        tie-osien-pituudet (when tierekisteri-valid?
                             (tieverkko-kyselyt/hae-osien-pituudet db {:tie (:tie suolarajoitus)
                                                                       :aosa (:aosa suolarajoitus)
                                                                       :losa (:losa suolarajoitus)}))
        pituus (when tierekisteri-valid?
                 (tieverkko-kyselyt/laske-tien-osien-pituudet tie-osien-pituudet suolarajoitus))
        ajoratojen-pituudet (when tierekisteri-valid?
                              (tieverkko-kyselyt/hae-ajoratojen-pituudet db {:tie (:tie suolarajoitus)
                                                                             :aosa (:aosa suolarajoitus)
                                                                             :losa (:losa suolarajoitus)}))
        yhdistetyt-ajoradat (mapv (fn [[osa data]]
                                     (let [ajoratatiedot {:osa osa ;; Osa talteen
                                                          :pituus (or (some #(when (and (= (:ajorata %) 0) (= (:osa %) osa)) (:pituus %)) ajoratojen-pituudet) 0) ;; Ensimmäisen ajoradan pituus talteen
                                                          :ajoratojen-pituus (some #(when (and (= (:ajorata %) 1) (= (:osa %) osa)) (:pituus %)) ajoratojen-pituudet) ;; Oletetaan, että kaikki loput ajoradat ovat saman mittaisia
                                                          :ajoratojen-maara (count (keep #(when (and (not= (:ajorata %) 0) (= (:osa %) osa)) %) ajoratojen-pituudet)) ;; Määritellään ajoratojen määrä (yleensa ajoratoja on 0,1,2) joten tähän tulisi arvo 2
                                                          }
                                           ;; Lasketaan vielä yhteen kokonaispituus, koska siitä pitää päätellä paljon asioita laskennassa
                                           ajoratatiedot (assoc ajoratatiedot :kokonaispituus (+ (:pituus ajoratatiedot) (or (:ajoratojen-pituus ajoratatiedot) 0)))]
                                       ajoratatiedot))
                               ;; Yhdistaä mahdolliset ajoradata samaan mäppiin
                               (group-by :osa ajoratojen-pituudet))
        ajoratojen-pituus (reduce (fn [summa ajorata]
                                    (let [pituus (tieverkko-kyselyt/laske-tien-osien-pituudet (conj [] ajorata) suolarajoitus)
                                          summa (+ summa (:pituus pituus))]
                                      summa))
                            0 yhdistetyt-ajoradat)
        ;; Haetaan pohjavesialueet annetun tierekisterin perusteella
        pohjavesialueet (when tierekisteri-valid?
                          (suolarajoitus-kyselyt/hae-leikkaavat-pohjavesialueet-tierekisterille db (select-keys suolarajoitus [:tie :aosa :aet :losa :let])))]
    ;; Palautetaan joko virheet, tai saadut tiedot
    (if validaatiovirheet
      {:validaatiovirheet validaatiovirheet}
      {:pituus (:pituus pituus)
       :ajoratojen_pituus ajoratojen-pituus
       :pohjavesialueet pohjavesialueet})))

(defn hae-tierekisterin-tiedot [db user {:keys [urakka-id] :as suolarajoitus}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-suunnittelu-suola user urakka-id)
  (let [tr-tiedot (tierekisterin-tiedot db suolarajoitus)]
    (if (:validaatiovirheet tr-tiedot)
      (transit-vastaus 400 (:validaatiovirheet tr-tiedot))
      tr-tiedot)))

(defn hae-suolarajoitukset [db user {:keys [urakka-id hoitokauden-alkuvuosi] :as tiedot}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-suunnittelu-suola user urakka-id)
  (log/debug "Suolarajoitus :: hae-suolarajoitukset :: tiedot " tiedot)
  (let [rajoitukset (suolarajoitus-kyselyt/hae-suolarajoitukset-hoitokaudelle db
                      {:hoitokauden-alkuvuosi hoitokauden-alkuvuosi
                       :urakka_id urakka-id})
        rajoitukset (map (fn [suolarajoitus]
                            (update suolarajoitus :pohjavesialueet
                              (fn [alueet]
                                (mapv
                                  #(konv/pgobject->map % :tunnus :string :nimi :string)
                                  (konv/pgarray->vector alueet)))))
                      rajoitukset)]
    rajoitukset))

(defn tallenna-suolarajoitus
  "Suolarajoitukset on tallennettu kahteen eri tietokantatauluun.
   Rajoitusalue -taulu sisältää sijaintitiedot ja rajoitusalue_rajoitus sisältää hoitovuosikohtaiset tiedot.
   Pohjavesialueita ei tallenneta rajoitukselle ollenkaan, vaan ne haetaan tierekisteriosoitteen perusteella aina tarvittaessa lennossa."
  [db user suolarajoitus]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-suunnittelu-suola user (:urakka_id suolarajoitus))
  (log/debug "tallenna-suolarajoitus :: suolarajoitus" suolarajoitus)
  (let [tr-tiedot (tierekisterin-tiedot db suolarajoitus)]
    (if (:validaatiovirheet tr-tiedot)
      (transit-vastaus 400 (:validaatiovirheet tr-tiedot))
      (jdbc/with-db-transaction [db db]
       (let [kopioidaan-tuleville-vuosille? (:kopioidaan-tuleville-vuosille? suolarajoitus)
             urakan-tiedot (first (urakat-kyselyt/hae-urakka db {:id (:urakka_id suolarajoitus)}))
             urakan-hoitovuodet (pvm/tulevat-hoitovuodet
                                  (:hoitokauden-alkuvuosi suolarajoitus)
                                  kopioidaan-tuleville-vuosille?
                                  urakan-tiedot)
             ;; Tallennetaan ensin rajoitusalue uutena tai päivityksenä
             db-rajoitusalue {:id (when (:rajoitusalue_id suolarajoitus) (:rajoitusalue_id suolarajoitus))
                              :tie (:tie suolarajoitus)
                              :aosa (:aosa suolarajoitus)
                              :aet (:aet suolarajoitus)
                              :losa (:losa suolarajoitus)
                              :let (:let suolarajoitus)
                              :pituus (:pituus suolarajoitus)
                              :ajoratojen_pituus (:ajoratojen_pituus suolarajoitus)
                              :urakka_id (:urakka_id suolarajoitus)
                              :kayttaja_id (:id user)}

             ;; Päivitä tai tallenna uutena
             rajoitusalue (if (:id db-rajoitusalue)
                            (let [;; Haetaan rajoitusalue kannasta, jotta voidaan verrata, onko tierekisteriosoite muuttunut
                                  vanha-rajoitusalue (first (suolarajoitus-kyselyt/hae-suolarajoitusalue db {:id (:id db-rajoitusalue)}))
                                  tierekisteri_muokattu? (tierekisteri-muokattu? db-rajoitusalue vanha-rajoitusalue)
                                  db-rajoitusalue (assoc db-rajoitusalue :tierekisteri_muokattu? tierekisteri_muokattu?)]
                              (do
                                (suolarajoitus-kyselyt/paivita-rajoitusalue! db db-rajoitusalue)
                                (first (suolarajoitus-kyselyt/hae-suolarajoitusalue db {:id (:rajoitusalue_id suolarajoitus)}))))
                            (let [vastaus (suolarajoitus-kyselyt/tallenna-rajoitusalue<! db (dissoc db-rajoitusalue :id))]
                              (first (suolarajoitus-kyselyt/hae-suolarajoitusalue db {:id (:id vastaus)}))))

             db-rajoitus {:id (:rajoitus_id suolarajoitus)
                          :rajoitusalue_id (:id rajoitusalue)
                          :suolarajoitus (:suolarajoitus suolarajoitus)
                          :formiaatti (:formiaatti suolarajoitus)
                          :kayttaja_id (:id user)}
             ;; Päivitä tai tallenna uutena
             _ (if (:id db-rajoitus)
                 (doseq [vuosi urakan-hoitovuodet]
                   (let [;; Haetaan päivitettävä rajoitus tietokannasta urakan ja vuoden perusteella. Meillä ei ole id:tä kaikille vuosille
                         haettu-rajoitus (first (suolarajoitus-kyselyt/hae-suolarajoitus db {:rajoitusalue_id (:id rajoitusalue)
                                                                                             :hoitokauden-alkuvuosi vuosi}))
                         rajoitus {:id (:rajoitus_id haettu-rajoitus)
                                   :kayttaja_id (:id user)
                                   :suolarajoitus (:suolarajoitus suolarajoitus)
                                   :hoitokauden-alkuvuosi (:hoitokauden-alkuvuosi haettu-rajoitus)
                                   :formiaatti (:formiaatti suolarajoitus)
                                   :rajoitusalue_id (:rajoitusalue_id haettu-rajoitus)}
                         _ (suolarajoitus-kyselyt/paivita-suolarajoitus! db rajoitus)]))
                 (doseq [vuosi urakan-hoitovuodet]
                   (let [rajoitus (assoc db-rajoitus :hoitokauden-alkuvuosi vuosi)
                         r (suolarajoitus-kyselyt/tallenna-suolarajoitus<! db (dissoc rajoitus :id))])))
             suolarajoitus (first (suolarajoitus-kyselyt/hae-suolarajoitus db {:rajoitusalue_id (:id rajoitusalue)
                                                                               :hoitokauden-alkuvuosi (:hoitokauden-alkuvuosi suolarajoitus)}))

             suolarajoitus (update suolarajoitus :pohjavesialueet
                             (fn [alueet]
                               (mapv
                                 #(konv/pgobject->map % :tunnus :string :nimi :string)
                                 (konv/pgarray->vector alueet))))]
         suolarajoitus)))))

(defn poista-suolarajoitus [db user {:keys [kopioidaan-tuleville-vuosille? urakka_id rajoitusalue_id hoitokauden-alkuvuosi]}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-suunnittelu-suola user urakka_id)
  (jdbc/with-db-transaction [db db]
    (log/debug "Suolarajoitus :: poista-suolarajoitus :: rajoitusalue_id:" rajoitusalue_id " hoitokauden-alkuvuosi: " hoitokauden-alkuvuosi)
    (let [;; Poistaa kaikki suolarajoitukset joilla on sama tai suurempi hoitokauden alkuvuosi, mikäli kopiointi on käytössä
         poistetut-rajoitukset (suolarajoitus-kyselyt/poista-suolarajoitus<! db (merge {:rajoitusalue_id rajoitusalue_id
                                                                                        :hoitokauden-alkuvuosi hoitokauden-alkuvuosi
                                                                                        :poista-tulevat nil}
                                                                                  (when kopioidaan-tuleville-vuosille?
                                                                                    {:poista-tulevat "true"})))
         ;; Jos suolarajoituksia ei jää rajoitusalue_rajoitus tauluun, niin poistetaan myös alkuperäinen rajoitusalue
         suolarajoitukset (suolarajoitus-kyselyt/hae-suolarajoitukset-rajoitusalueelle db {:rajoitusalue_id rajoitusalue_id})
         poistetut-rajoitusalueet (when (empty? suolarajoitukset)
                                    (suolarajoitus-kyselyt/poista-suolarajoitusalue<! db {:id rajoitusalue_id}))]
     (if (nil? poistetut-rajoitukset)
       (transit-vastaus 400 {:virhe "Suolarajoituksen poistaminen epäonnistui"})
       "OK"))))

(defn hae-talvisuolan-kayttorajat-mhu
  "Talvisuolan käyttöraja tulee urakka_tehtavamaara tauluun tallennetun suolauksen määrästä. Sanktiot ja indeksi tulevat suolasakko taulusta"
  [db user {:keys [urakka-id hoitokauden-alkuvuosi] :as tiedot}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-suunnittelu-suola user urakka-id)

  (log/debug "hae-talvisuolan-kayttoraja :: tiedot" tiedot)

  (jdbc/with-db-transaction [db db]
   (let [kokonaismaara (first (suolarajoitus-kyselyt/hae-talvisuolan-kokonaiskayttoraja db
                                {:urakka-id urakka-id
                                 :hoitokauden-alkuvuosi hoitokauden-alkuvuosi}))

         talvisuolan-sanktiot (first (suolarajoitus-kyselyt/hae-talvisuolan-sanktiot db
                                       {:urakka-id urakka-id
                                        :hoitokauden-alkuvuosi hoitokauden-alkuvuosi}))
         ;; Lisää kokonaismäärä myös talvisuolan-sanktioihin
         talvisuolan-sanktiot (assoc talvisuolan-sanktiot :talvisuolan-kayttoraja (:talvisuolan_kayttoraja kokonaismaara))

         ;; Vain MHU urakoissa määritellään rajoitusalueidelle suolasanktio
         rajoitusalueiden-suolasanktio (first (suolarajoitus-kyselyt/hae-rajoitusalueiden-suolasanktio db
                                                {:urakka-id urakka-id
                                                 :hoitokauden-alkuvuosi hoitokauden-alkuvuosi}))]

     {:talvisuolan-sanktiot (or talvisuolan-sanktiot {})
      :rajoitusalueiden-suolasanktio (or rajoitusalueiden-suolasanktio {})})))

(defn hae-talvisuolan-kayttorajat-alueurakka
  "Alueurakan sakot ja käyttöraja tulevat suolasakko-taulusta"
  [db user {:keys [urakka-id hoitokauden-alkuvuosi] :as tiedot}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-suunnittelu-suola user urakka-id)
  (log/debug "hae-talvisuolan-kayttorajat-alueurakka :: tiedot" tiedot)

  (let [talvisuolan-sanktiot (first (suolarajoitus-kyselyt/hae-talvisuolan-kayttoraja-alueurakka db
                                      {:urakka-id urakka-id
                                       :hoitokauden-alkuvuosi hoitokauden-alkuvuosi}))]
    {:talvisuolan-sanktiot (or talvisuolan-sanktiot {})}))

(defn hae-talvisuolan-kayttorajat
  [db user {:keys [urakka-id hoitokauden-alkuvuosi] :as tiedot}]

  (let [urakkatyyppi (keyword (:tyyppi (first (urakat-kyselyt/hae-urakan-tyyppi db urakka-id))))
        vastaus (case urakkatyyppi
                  :teiden-hoito
                  (hae-talvisuolan-kayttorajat-mhu db user tiedot)

                  :hoito
                  (hae-talvisuolan-kayttorajat-alueurakka db user tiedot)
                  (transit-vastaus 400 {:virhe (str "Urakan tyyppi on virheellinen: " urakkatyyppi)}))]
    vastaus))

(defn tallenna-talvisuolan-kayttoraja-mhu
  "Funktio ei nimestään huolimatta tallenna talvisuolan käyttörajoja, koska ne tallennetaan Tehtävät ja määrät sivulla.
  Tässä tallennetaan kokonaismäärälle sanktiot ja indeksi.
  Käyttöraja mäpin sisältö:
  {:urakka-id <id>
   :hoitokauden-alkuvuosi <vuosi>
   :indeksi <indeksin nimi>
   :sanktio_ylittavalta_tonnilta <maara>
   :kopioidaan-tuleville-vuosille? <true/false>}"
  [db user {:keys [urakka-id hoitokauden-alkuvuosi] :as kayttoraja}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-suunnittelu-suola user urakka-id)
  (log/debug "tallenna-talvisuolan-kayttoraja :: kayttoraja" kayttoraja)
  (jdbc/with-db-transaction [db db]
   (let [kopioidaan-tuleville-vuosille? (:kopioidaan-tuleville-vuosille? kayttoraja)
         urakan-tiedot (first (urakat-kyselyt/hae-urakka db {:id urakka-id}))
         urakan-hoitovuodet (pvm/tulevat-hoitovuodet hoitokauden-alkuvuosi kopioidaan-tuleville-vuosille? urakan-tiedot)

         ;; Päivitä tiedot tai tallenna uusi
         _ (doseq [vuosi urakan-hoitovuodet]
             (let [;; Hae käyttöraja kannasta, jos siellä olisi jo olemassa jotain pohjaa
                   hoitovuoden-kayttoraja (first (suolarajoitus-kyselyt/hae-talvisuolan-sanktiot db
                                                   {:urakka-id urakka-id
                                                    :hoitokauden-alkuvuosi vuosi}))
                   ;; Jätetaan mahdolliset id yms, tiedot jäljelle, jos tietokannassa oli jo olemassa jotain
                   hoitovuoden-kayttoraja (-> hoitovuoden-kayttoraja
                                            (assoc :urakka-id urakka-id)
                                            ;; Asetetaan aina indeksi tyhjäksi, koska mhu/hju urakoiden talvisuolan
                                            ;;   käyttörajalle ei aseteta indeksiä
                                            (assoc :indeksi nil)
                                            (assoc :sanktio_ylittavalta_tonnilta (:sanktio_ylittavalta_tonnilta kayttoraja))
                                            (assoc :kayttaja-id (:id user))
                                            (assoc :hoitokauden-alkuvuosi vuosi)
                                            (assoc :kaytossa true)) ;; Mahdollistetaan sanktioiden kääntäminen pois päältä

                   _ (if (:id hoitovuoden-kayttoraja)
                       (suolarajoitus-kyselyt/paivita-talvisuolan-kayttoraja! db hoitovuoden-kayttoraja)
                       (suolarajoitus-kyselyt/tallenna-talvisuolan-kayttoraja! db hoitovuoden-kayttoraja))]))
         vastaus (first (suolarajoitus-kyselyt/hae-talvisuolan-sanktiot db {:urakka-id urakka-id
                                                                            :hoitokauden-alkuvuosi hoitokauden-alkuvuosi}))

         ;; Lisää vielä kokonaismäärä vastaukseen
         kokonaismaara (first (suolarajoitus-kyselyt/hae-talvisuolan-kokonaiskayttoraja db
                                {:urakka-id urakka-id
                                 :hoitokauden-alkuvuosi hoitokauden-alkuvuosi}))
         vastaus (assoc vastaus :talvisuolan-kayttoraja (:talvisuolan_kayttoraja kokonaismaara))]
     vastaus)))

(defn tallenna-talvisuolan-kayttoraja-alueurakka
  [db user {:keys [urakka-id hoitokauden-alkuvuosi] :as kayttoraja}]
  (log/debug "tallenna suolasakko" kayttoraja)
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-suunnittelu-suola user urakka-id)

  (jdbc/with-db-transaction [db db]
   (let [urakan-tiedot (first (urakat-kyselyt/hae-urakka db {:id urakka-id}))
         indeksi (:indeksi urakan-tiedot)
         hoitovuoden-kayttoraja (first (suolarajoitus-kyselyt/hae-talvisuolan-sanktiot db
                                         {:urakka-id urakka-id
                                          :hoitokauden-alkuvuosi hoitokauden-alkuvuosi}))
         params {:suolasakko-tai-bonus-maara (:suolasakko-tai-bonus-maara kayttoraja)
                 :vain-sakko-maara (:vain-sakko-maara kayttoraja)
                 :maksukuukausi (:maksukuukausi kayttoraja)
                 ;; Asetetaan aina tallentaessa indeksiksi alueurakan oma indeksi, esim. Maku 2010
                 :indeksi indeksi
                 :kayttaja (:id user)
                 :talvisuolan-kayttoraja (:talvisuolan-kayttoraja kayttoraja)
                 :urakka-id urakka-id
                 :suolasakko-kaytossa (boolean (:suolasakko-kaytossa kayttoraja))
                 :hoitokauden_alkuvuosi hoitokauden-alkuvuosi
                 :id (:id hoitovuoden-kayttoraja)}]
     (if (:id hoitovuoden-kayttoraja)
       (do
         (suolarajoitus-kyselyt/paivita-talvisuolan-kayttoraja-alueurakka! db params)
         (:id hoitovuoden-kayttoraja))
       (:id (suolarajoitus-kyselyt/tallenna-talvisuolan-kayttoraja-alueurakka<! db params)))

     ;; Muodosta vastaus
     (let [vastaus (first (suolarajoitus-kyselyt/hae-talvisuolan-kayttoraja-alueurakka db
                            {:urakka-id urakka-id
                             :hoitokauden-alkuvuosi hoitokauden-alkuvuosi}))]
       vastaus))))


(defn tallenna-talvisuolan-kayttoraja
  [db user {:keys [urakka-id hoitokauden-alkuvuosi] :as kayttoraja}]
  (let [urakkatyyppi (keyword (:tyyppi (first (urakat-kyselyt/hae-urakan-tyyppi db urakka-id))))]
    (case urakkatyyppi
      :teiden-hoito
      (tallenna-talvisuolan-kayttoraja-mhu db user kayttoraja)

      :hoito
      (tallenna-talvisuolan-kayttoraja-alueurakka db user kayttoraja)

      (transit-vastaus 400 {:virhe (str "Urakan tyyppi on virheellinen: " urakkatyyppi)}))))

(defn tallenna-rajoitusalueen-sanktio [db user {:keys [urakka-id hoitokauden-alkuvuosi kopioidaan-tuleville-vuosille?] :as sanktio}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-suunnittelu-suola user urakka-id)
  (log/debug "tallenna-rajoitusalueen-sanktio :: sanktio" sanktio)
  (jdbc/with-db-transaction [db db]
   (let [urakan-tiedot (first (urakat-kyselyt/hae-urakka db {:id urakka-id}))
         indeksi (:indeksi urakan-tiedot)
         urakan-hoitovuodet (pvm/tulevat-hoitovuodet hoitokauden-alkuvuosi kopioidaan-tuleville-vuosille? urakan-tiedot)
         ;; Päivitä tiedot tai luo uusi

         _ (doseq [vuosi urakan-hoitovuodet]
             (let [;; Haetaan mahdollinen sanktio kannasta
                   hoitovuoden-rajoitusalue-sanktio (first (suolarajoitus-kyselyt/hae-rajoitusalueiden-suolasanktio db
                                                             {:urakka-id urakka-id
                                                              :hoitokauden-alkuvuosi vuosi}))
                   hoitovuoden-rajoitusalue-sanktio (-> hoitovuoden-rajoitusalue-sanktio
                                                      (assoc :urakka-id urakka-id)
                                                      ;; Asetetaan aina tallentaessa indeksiksi alueurakan oma indeksi, esim. Maku 2015
                                                      (assoc :indeksi indeksi)
                                                      (assoc :sanktio_ylittavalta_tonnilta (:sanktio_ylittavalta_tonnilta sanktio))
                                                      (assoc :kayttaja-id (:id user))
                                                      (assoc :hoitokauden-alkuvuosi vuosi)
                                                      (assoc :kaytossa true))
                   _ (if (:id hoitovuoden-rajoitusalue-sanktio)
                       (suolarajoitus-kyselyt/paivita-rajoitusalueen-suolasanktio! db hoitovuoden-rajoitusalue-sanktio)
                       (suolarajoitus-kyselyt/tallenna-rajoitusalueen-suolasanktio! db hoitovuoden-rajoitusalue-sanktio))]))
         vastaus (first (suolarajoitus-kyselyt/hae-rajoitusalueiden-suolasanktio db {:urakka-id urakka-id
                                                                                     :hoitokauden-alkuvuosi hoitokauden-alkuvuosi}))]
     vastaus)))

(defn hae-suolatoteumat-rajoitusalueittain [db user {:keys [hoitokauden-alkuvuosi alkupvm loppupvm urakka-id] :as tiedot}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-toteumat-suola user urakka-id)
  (log/debug "hae-suolatoteumat-rajoitusalueittain :: tiedot" (pr-str tiedot))
  (suolarajoitus-kyselyt/hae-suolatoteumat-rajoitusalueittain db tiedot))

(defn hae-rajoitusalueen-summatiedot
  "Haetaan päivittäin groupatut suolatoteumat halutulle rajoitusalueelle"
  [db user {:keys [rajoitusalue-id alkupvm loppupvm urakka-id] :as tiedot}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-toteumat-suola user urakka-id)
  (log/debug "hae-rajoitusalueen-summatiedot :: tiedot" (pr-str tiedot))
  (let [suolatoteumat (suolarajoitus-kyselyt/hae-rajoitusalueen-suolatoteumasummat db
                        {:urakka-id urakka-id
                         :rajoitusalue-id rajoitusalue-id
                         :alkupvm (c/to-sql-time alkupvm)
                         :loppupvm (c/to-sql-time loppupvm)})
        suolatoteumat (mapv (fn [rivi]
                              (-> rivi
                                (assoc :maara (or (:formiaattimaara rivi) (:suolamaara rivi)))
                                (assoc :lukumaara (or (:formiaattilukumaara rivi) (:suolalukumaara rivi)))
                                (assoc :rivi-id (hash rivi))))
                        suolatoteumat)]
    suolatoteumat))

(defn hae-rajoitusalueen-paivan-toteumat
  "Haetaan yhden päivän toteumat rajoitusalueelle materiaali-id:n perusteella"
  [db user {:keys [rajoitusalue-id pvm materiaali-id urakka-id koneellinen?] :as tiedot}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-toteumat-suola user urakka-id)
  (log/debug "hae-rajoitusalueen-paivan-toteumat :: tiedot" (pr-str tiedot))
  (let [alkupaiva (c/to-sql-time pvm)
        loppupaiva (c/to-sql-time (pvm/ajan-muokkaus (pvm/joda-timeksi pvm) true 1 :paiva))
        paivan-toteumat (suolarajoitus-kyselyt/hae-rajoitusalueen-paivan-toteumat db
                          {:urakka-id urakka-id
                           :rajoitusalue-id rajoitusalue-id
                           :materiaali-id materiaali-id
                           :koneellinen? koneellinen?
                           :alkupvm alkupaiva
                           :loppupvm loppupaiva})]
    paivan-toteumat))

(defn hae-pohjavesialueidenurakat [db user tiedot]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/hallinta-pohjavesialueidensiirto user)
  (log/debug "hae-pohjavesialueidenurakat :: urakat" hae-pohjavesialueidenurakat)
  (let [urakat (suolarajoitus-kyselyt/hae-pohjavesialueidenurakat db)]
    urakat))

(defn hae-urakan-siirrettavat-pohjavesialueet [db user tiedot]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-toteumat-suola user (:urakkaid tiedot))
  (log/debug "hae-urakan-siirrettavat-pohjavesialueet :: tiedot" tiedot)
  (let [;; Haetaan urakan tiedot ja tarvittaessa luodaan pohjavesialue_talvisuola riveistä jokaiselle vuodelle oma instanssi
        urakkatiedot (first (urakat-kyselyt/hae-urakka db {:id (:urakkaid tiedot)}))
        urakan-alkuvuosi (pvm/vuosi (:alkupvm urakkatiedot))
        urakan-loppuvuosi (pvm/vuosi (:loppupvm urakkatiedot))
        urakan-vuodet (range urakan-alkuvuosi urakan-loppuvuosi)
        alueet (suolarajoitus-kyselyt/hae-urakan-siirrettavat-pohjavesialueet db {:urakkaid (:urakkaid tiedot)})
        ;; Lisää pituudet alueille
        alueet (map (fn [alue]
                      (let [tierekisterin-tiedot (tierekisterin-tiedot db alue)]
                        (-> alue
                          (assoc :ajoratojen_pituus (:ajoratojen_pituus tierekisterin-tiedot))
                          (assoc :pituus (:pituus tierekisterin-tiedot)))))
                 alueet)]
    alueet))

(defn siirra-urakan-pohjavesialueet [db user tiedot]
  (log/debug "siirra-urakan-pohjavesialueet :: tiedot" tiedot)
  (let [urakkaid (:urakkaid tiedot)
        urakkatiedot (first (urakat-kyselyt/hae-urakka db {:id urakkaid}))
        urakan-loppuvuosi (pvm/vuosi (:loppupvm urakkatiedot))
        urakan-pohjavesialueet (:pohjavesialueet tiedot)
        _ (doseq [pohjavesialue urakan-pohjavesialueet]
            (let [pohjavesialueen-vuosi (:hoitokauden-alkuvuosi pohjavesialue)

                  tallennettava-suolarajoitus {:hoitokauden-alkuvuosi pohjavesialueen-vuosi
                                               :kopioidaan-tuleville-vuosille? true
                                               :urakka_id urakkaid
                                               :tie (:tie pohjavesialue)
                                               :aosa (:aosa pohjavesialue)
                                               :aet (:aet pohjavesialue)
                                               :losa (:losa pohjavesialue)
                                               :let (:let pohjavesialue)
                                               :suolarajoitus (:talvisuolaraja pohjavesialue)
                                               :formiaatti (if (= 0 (:talvisuolaraja pohjavesialue)) true false)
                                               :kayttaja_id (:luoja pohjavesialue)}
                  tr-tiedot (tierekisterin-tiedot db tallennettava-suolarajoitus)
                  tallennettava-suolarajoitus (-> tallennettava-suolarajoitus
                                                (assoc :pituus (:pituus tr-tiedot))
                                                (assoc :ajoratojen_pituus (:ajoratojen_pituus tr-tiedot)))]
              ;; Tallennetaan ensin rajoitusalue uutena tai päivityksenä
              (tallenna-suolarajoitus db user tallennettava-suolarajoitus)))]
    urakan-pohjavesialueet))

(defn tarkista-onko-suolatoteumia [db user tiedot]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-toteumat-suola user (:urakka-id tiedot))
  (let [onko? (:exists (first (suolarajoitus-kyselyt/onko-urakalla-suolatoteumia db {:urakka-id (:urakka-id tiedot)})))]
    onko?))

(defrecord Suolarajoitus []
  component/Lifecycle
  (start [this]
    (julkaise-palvelu (:http-palvelin this)
      :hae-suolarajoitukset
      (fn [user tiedot]
        (hae-suolarajoitukset (:db this) user tiedot)))

    (julkaise-palvelu (:http-palvelin this)
      :tallenna-suolarajoitus
      (fn [user tiedot]
        (tallenna-suolarajoitus (:db this) user tiedot)))

    (julkaise-palvelu (:http-palvelin this)
      :poista-suolarajoitus
      (fn [user tiedot]
        (poista-suolarajoitus (:db this) user tiedot)))

    ;; Tierekisteriosoitteen perusteella lasketaan ajoratojen pituus, reitin pituus sekä päätellään pohjavesialue/alueet
    (julkaise-palvelu (:http-palvelin this)
      :tierekisterin-tiedot
      (fn [user tiedot]
        (hae-tierekisterin-tiedot (:db this) user tiedot)))

    (julkaise-palvelu (:http-palvelin this)
      :hae-talvisuolan-kayttorajat
      (fn [user tiedot]
        (hae-talvisuolan-kayttorajat (:db this) user tiedot)))

    (julkaise-palvelu (:http-palvelin this)
      :tallenna-talvisuolan-kayttoraja
      (fn [user tiedot]
        (tallenna-talvisuolan-kayttoraja (:db this) user tiedot)))

    (julkaise-palvelu (:http-palvelin this)
      :tallenna-rajoitusalueen-sanktio
      (fn [user tiedot]
        (tallenna-rajoitusalueen-sanktio (:db this) user tiedot)))

    (julkaise-palvelu (:http-palvelin this)
      :hae-suolatoteumat-rajoitusalueittain
      (fn [user tiedot]
        (hae-suolatoteumat-rajoitusalueittain (:db this) user tiedot)))

    (julkaise-palvelu (:http-palvelin this)
      :hae-rajoitusalueen-summatiedot
      (fn [user tiedot]
        (hae-rajoitusalueen-summatiedot (:db this) user tiedot)))

    (julkaise-palvelu (:http-palvelin this)
      :hae-rajoitusalueen-paivan-toteumat
      (fn [user tiedot]
        (hae-rajoitusalueen-paivan-toteumat (:db this) user tiedot)))

    ;; Käytetään lyhyen aikaa hallintapuolelta, jotta rajoitusalueet saadaan muodostettua pohjavesialueiden perusteella
    (julkaise-palvelu (:http-palvelin this)
      :hae-pohjavesialueurakat
      (fn [user tiedot]
        (hae-pohjavesialueidenurakat (:db this) user tiedot)))

    (julkaise-palvelu (:http-palvelin this)
      :hae-urakan-siirrettavat-pohjavesialueet
      (fn [user tiedot]
        (hae-urakan-siirrettavat-pohjavesialueet (:db this) user tiedot)))

    (julkaise-palvelu (:http-palvelin this)
      :siirra-urakan-pohjavesialueet
      (fn [user tiedot]
        (siirra-urakan-pohjavesialueet (:db this) user tiedot)))

    (julkaise-palvelu (:http-palvelin this)
      :tarkista-onko-suolatoteumia
      (fn [user tiedot]
        (tarkista-onko-suolatoteumia (:db this) user tiedot)))
    this)

  (stop [this]
    (poista-palvelut (:http-palvelin this)
      :hae-suolarajoitukset
      :tallenna-suolarajoitus
      :poista-suolarajoitus
      :tierekisterin-tiedot
      :hae-talvisuolan-kayttorajat
      :tallenna-talvisuolan-kayttoraja
      :tallenna-rajoitusalueen-sanktio
      :hae-suolatoteumat-rajoitusalueittain
      :hae-rajoitusalueen-summatiedot
      :hae-rajoitusalueen-paivan-toteumat
      :hae-pohjavesialueurakat
      :hae-urakan-siirrettavat-pohjavesialueet
      :siirra-urakan-pohjavesialueet
      :tarkista-onko-suolatoteumia)
    this))
