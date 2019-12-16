(ns harja.palvelin.palvelut.valitavoitteet.valtakunnalliset-valitavoitteet
  "Palvelu valtakunnallisten välitavoitteiden hakemiseksi ja tallentamiseksi."
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.kyselyt.valitavoitteet :as q]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.konversio :as konv]
            [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [harja.domain.oikeudet :as oikeudet]
            [harja.pvm :as pvm]
            [clj-time.coerce :as c]
            [clj-time.core :as t]))

(defn hae-valtakunnalliset-valitavoitteet [db user]
  ;(;(oikeudet/vaadi-lukuoikeus oikeudet/hallinta-valitavoitteet user)
  (into []
        (map #(konv/string->keyword % :urakkatyyppi :tyyppi))
        (q/hae-valtakunnalliset-valitavoitteet db)))

(defn- poista-poistetut-valtakunnalliset-valitavoitteet
  "Poistaa valtakunnallisen välitavoitteen.

  Välitavoitteeseen linkitetyt urakkakohtaiset välitavoitteet poistetaan vain käynnissä olevista
  ja tulevista urakoista ja vain silloin jos välitavoite ei ole valmistunut.
  Toisin sanoen välitavoite jää näkyviin vanhoihin urakoihin tai jos se on ehditty tehdä valmiiksi."
  [db user valitavoitteet]
  (doseq [poistettava (filter :poistettu valitavoitteet)]
    (let [linkitetyt (into []
                           (map konv/alaviiva->rakenne)
                           (q/hae-valitavoitteeseen-linkitetyt-valitavoitteet db (:id poistettava)))
          urakka-kaynnissa-tai-tulossa? (fn [urakka]
                                          (or (pvm/valissa?
                                                (t/now)
                                                (c/from-date (:alkupvm urakka))
                                                (c/from-date (:loppupvm urakka)))
                                              (pvm/jalkeen? (c/from-date (:alkupvm urakka)) (t/now))))
          poistettavat-linkitetyt (filter
                                    (fn [valitavoite]
                                      (and (urakka-kaynnissa-tai-tulossa? (:urakka valitavoite))
                                           (nil? (:valmispvm valitavoite))))
                                    linkitetyt)]
      (log/debug "Poistetaan valtakunnallinen valitavoite " (:id poistettava))
      (q/poista-valtakunnallinen-valitavoite! db
                                              (:id user)
                                              (:id poistettava))
      (log/debug "Poistetaan tavoitteeseen kopioidut urakkakohtaiset tavoitteet")
      (doseq [poistettava poistettavat-linkitetyt]
        (q/poista-urakan-valitavoite! db
                                      (:id user)
                                      (get-in poistettava [:urakka :id])
                                      (:id poistettava))))))

(defn kopioi-valtakunnallinen-kertaluontoinen-valitavoite-sopiviin-urakoihin
  "Kopioi valtakunnallisen kertaluontoisen välitavoitteen annettuihin urakoihin
  seuraavien ehtojen mukaisesti:

  Jos takaraja on annettu, kopioidaan välitavoite urakkaan jos
  se on valittua tyyppiä, ei ole päättynyt ja takaraja osuu urakan voimassaoloajalle.

  Jos takarajaa ei ole annettu, kopioidaan välitavoite urakkaan jos
  se on valittua tyyppiä."
  [db user {:keys [takaraja urakkatyyppi nimi] :as valitavoite}
   valtakunnallinen-valitavoite-id urakat]
  (let [linkitettavat-urakat (if takaraja
                               (filter
                                 (fn [urakka]
                                   (and
                                     (= (pvm/ennen? (t/now) (c/from-date (:loppupvm urakka))))
                                     (= urakkatyyppi (if (= :teiden-hoito (:tyyppi urakka))
                                                       :hoito
                                                       (:tyyppi urakka)))
                                     (pvm/valissa? (c/from-date takaraja)
                                                   (c/from-date (:alkupvm urakka))
                                                   (c/from-date (:loppupvm urakka)))))
                                 urakat)
                               (filter
                                 #(and (= urakkatyyppi (if (= :teiden-hoito (:tyyppi %))
                                                         :hoito
                                                         (:tyyppi %)))
                                       (= (pvm/ennen? (t/now) (c/from-date (:loppupvm %)))))
                                 urakat))]
    (doseq [urakka linkitettavat-urakat]
      (log/debug "Lisätään kertaluontoinen välitavoite " nimi " urakkaan " (:nimi urakka))
      (q/lisaa-urakan-valitavoite<! db {:urakka (:id urakka)
                                        :aloituspvm nil
                                        :takaraja (konv/sql-date takaraja)
                                        :nimi nimi
                                        :yllapitokohde nil
                                        :valtakunnallinen_valitavoite valtakunnallinen-valitavoite-id
                                        :luoja (:id user)}))))

(defn- luo-uudet-valtakunnalliset-kertaluontoiset-valitavoitteet
  "Luo uudet valtakunnalliset kertaluontoisten välitavoitteet ja aloittaa niiden
  kopioinnin annettuihin urakoihin."
  [db user valitavoitteet urakat]
  (doseq [{:keys [takaraja nimi urakkatyyppi] :as valitavoite} valitavoitteet]
    (let [id (:id (q/lisaa-valtakunnallinen-kertaluontoinen-valitavoite<!
                    db
                    {:takaraja (konv/sql-date takaraja)
                     :nimi nimi
                     :urakkatyyppi (name urakkatyyppi)
                     :tyyppi "kertaluontoinen"
                     :luoja (:id user)}))]
      (kopioi-valtakunnallinen-kertaluontoinen-valitavoite-sopiviin-urakoihin db
                                                                              user
                                                                              valitavoite
                                                                              id
                                                                              urakat))))

(defn kopioi-valtakunnallinen-toistuva-valitavoite-sopiviin-urakoihin
  "Luo välitavoitteen annettuihin urakoihin kertaalleen per jäljellä oleva urakkavuosi,
  jos urakka on annettua tyyppiä eikä se ole päättynyt.

  Ei kopioi välitavoitetta jos takarajaksi muodostuu aika, joka ei ole
  urakan voimassaoloaikana tai on ennen järjestelmän käyttöönottoa."
  [db user {:keys [takaraja-toistopaiva urakkatyyppi takaraja-toistokuukausi nimi] :as valitavoite}
   valtakunnallinen-valitavoite-id urakat]
  (let [linkitettavat-urakat (filter
                               #(and (= urakkatyyppi (if (= :teiden-hoito (:tyyppi %))
                                                       :hoito
                                                       (:tyyppi %)))
                                     (= (pvm/ennen? (t/now) (c/from-date (:loppupvm %)))))
                               urakat)
        _ (println " LINKITETTÄVÄ linkitettavat-urakat " linkitettavat-urakat)]
    (doseq [urakka linkitettavat-urakat]
      (let [urakan-jaljella-olevat-vuodet (range (max (t/year (t/now))
                                                      (t/year (c/from-date (:alkupvm urakka))))
                                                 (inc (t/year (c/from-date (:loppupvm urakka)))))]
        (doseq [vuosi urakan-jaljella-olevat-vuodet]
          (let [tarkka-takaraja (t/local-date vuosi takaraja-toistokuukausi takaraja-toistopaiva)]
            (when (and (t/after? tarkka-takaraja pvm/kayttoonottto)
                       (pvm/valissa? tarkka-takaraja
                                     (c/from-date (:alkupvm urakka))
                                     (c/from-date (:loppupvm urakka))))
              (log/debug "Lisätään toistuva välitavoite " nimi " urakkaan " (:nimi urakka) " takarajalla "
                         vuosi "-" takaraja-toistokuukausi "-" takaraja-toistopaiva)
              (q/lisaa-urakan-valitavoite<! db {:urakka (:id urakka)
                                                :aloituspvm nil
                                                :takaraja (konv/sql-date (c/to-date tarkka-takaraja))
                                                :nimi nimi
                                                :yllapitokohde nil
                                                :valtakunnallinen_valitavoite valtakunnallinen-valitavoite-id
                                                :luoja (:id user)}))))))))

(defn- luo-uudet-valtakunnalliset-toistuvat-valitavoitteet
  "Luo uudet valtakunnalliset toistuvat välitavoitteet ja aloittaa niiden kopioinnin urakoihin."
  [db user valitavoitteet urakat]
  (doseq [{:keys [nimi urakkatyyppi takaraja-toistopaiva takaraja-toistokuukausi] :as valitavoite}
          valitavoitteet]
    (let [id (:id (q/lisaa-valtakunnallinen-toistuva-valitavoite<!
                    db
                    {:nimi nimi
                     :urakkatyyppi (name urakkatyyppi)
                     :takaraja_toistopaiva takaraja-toistopaiva
                     :takaraja_toistokuukausi takaraja-toistokuukausi
                     :tyyppi "toistuva"
                     :luoja (:id user)}))]
      (kopioi-valtakunnallinen-toistuva-valitavoite-sopiviin-urakoihin db user valitavoite id urakat))))

(defn- luo-uudet-valtakunnalliset-valitavoitteet [db user valitavoitteet urakat]
  (luo-uudet-valtakunnalliset-kertaluontoiset-valitavoitteet db
                                                             user
                                                             (filter #(and (= (:tyyppi %) :kertaluontoinen)
                                                                           (< (:id %) 0)
                                                                           (not (:poistettu %)))
                                                                     valitavoitteet)
                                                             urakat)
  (luo-uudet-valtakunnalliset-toistuvat-valitavoitteet db
                                                       user
                                                       (filter #(and (= (:tyyppi %) :toistuva)
                                                                     (< (:id %) 0)
                                                                     (not (:poistettu %)))
                                                               valitavoitteet)
                                                       urakat))

(defn- paivita-valtakunnalliseen-valitavoitteeseen-linkitetyt-valitavoitteet
  "Päivittää valtakunnalliseen välitavoitteeseen linkitetyt urakkakohtaiset välitavoitteet
   mikäli niitä ei ole muokattu urakassa."
  [db user {:keys [id nimi takaraja] :as valitavoite} urakat]
  (cond (= (:tyyppi valitavoite) :kertaluontoinen)
        (q/paivita-kertaluontoiseen-valitavoitteeseen-linkitetty-muokkaamaton-valitavoite!
          db
          {:nimi nimi
           :takaraja (konv/sql-date takaraja)
           :id id})

        (= (:tyyppi valitavoite) :toistuva)
        (do
          (log/debug "Päivitetään valtakunnallisen toistuvan välitavoitteen " (:id valitavoite) "urakkakohtaiset
          linkitetyt välitavoitteet poistamalla vanhat ja lisäämällä uudet tilalle")
          (q/poista-toistuvaan-valitavoitteeseen-linkitetty-muokkaamaton-valitavoite! db {:id id})
          (kopioi-valtakunnallinen-toistuva-valitavoite-sopiviin-urakoihin db
                                                                           user
                                                                           valitavoite
                                                                           id
                                                                           urakat))
        :default
        nil))

(defn- paivita-valtakunnalliset-valitavoitteet [db user valitavoitteet urakat]
  (doseq [{:keys [id takaraja takaraja-toistopaiva takaraja-toistokuukausi nimi] :as valitavoite}
          (filter #(and (> (:id %) 0)
                        (not (:poistettu %))) valitavoitteet)]
    (q/paivita-valtakunnallinen-valitavoite! db nimi
                                             (konv/sql-date takaraja)
                                             takaraja-toistopaiva
                                             takaraja-toistokuukausi
                                             (:id user)
                                             id)
    (paivita-valtakunnalliseen-valitavoitteeseen-linkitetyt-valitavoitteet db
                                                                           user
                                                                           valitavoite
                                                                           urakat)))


(defn tallenna-valtakunnalliset-valitavoitteet! [db user {:keys [valitavoitteet]}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/hallinta-valitavoitteet user)
  (log/debug "Tallenna valtakunnalliset välitavoitteet " (pr-str valitavoitteet))
  (jdbc/with-db-transaction [db db]
    (let [urakat-kaynnissa-tai-tulossa (into []
                                             (map #(konv/string->keyword % :tyyppi))
                                             (urakat-q/hae-kaynnissa-olevat-ja-tulevat-urakat db))]
      (poista-poistetut-valtakunnalliset-valitavoitteet db user valitavoitteet)
      (luo-uudet-valtakunnalliset-valitavoitteet db user valitavoitteet urakat-kaynnissa-tai-tulossa)
      (paivita-valtakunnalliset-valitavoitteet db user valitavoitteet urakat-kaynnissa-tai-tulossa)
      (hae-valtakunnalliset-valitavoitteet db user))))
