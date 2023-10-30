(ns harja.palvelin.palvelut.budjettisuunnittelu
  (:require [com.stuartsierra.component :as component]
            [clojure.java.jdbc :as jdbc]
            [specql.core :refer [fetch update! insert!]]
            [specql.op :as op]
            [harja.palvelin.asetukset :refer [ominaisuus-kaytossa?]]

            [harja.tyokalut.yleiset :refer [round2]]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.pvm :as pvm]
            [harja.kyselyt
             [budjettisuunnittelu :as q]
             [urakat :as urakat-q]
             [kiinteahintaiset-tyot :as kiin-q]
             [kustannusarvioidut-tyot :as ka-q]
             [toimenpideinstanssit :as tpi-q]
             [indeksit :as i-q]
             [konversio :as konv]]
            [harja.palvelin.palvelut
             [kiinteahintaiset-tyot :as kiinthint-tyot]
             [kustannusarvioidut-tyot :as kustarv-tyot]]
            [harja.domain
             [oikeudet :as oikeudet]
             [budjettisuunnittelu :as bs]
             [toimenpidekoodi :as tpk]
             [tehtavaryhma :as tr]
             [urakka :as ur]
             [mhu :as mhu]]
            [harja.domain.palvelut.budjettisuunnittelu :as bs-p]
            [taoensso.timbre :as log]
            [clojure.string :as string]))

(declare hae-urakan-indeksikertoimet)

;; ---- IndeksikorjauksetSTART ----

(defn indeksikerroin
  "Palauttaa indeksikertoimen annetulle hoitovuoden järjestysnumerolle."
  [urakan-indeksit hoitovuosi-nro]
  (let [{:keys [indeksikerroin]} (get urakan-indeksit (dec hoitovuosi-nro))]
    indeksikerroin))

(defn indeksikorjaa
  ([indeksikerroin summa]
   (when (and indeksikerroin summa)
     ;; Laske indeksikorjaus ja pyöristä tulos kuuden desimaalin tarkkuuteen
     (round2 6 (* summa indeksikerroin)))))



(defmulti vahvista-tai-kumoa-indeksikorjaukset!
  (fn [taulun-tyyppi db vahvista? {:keys [urakka-id hoitovuosi-nro hoitokauden-alkupvm hoitokauden-loppupvm
                                          vahvistaja vahvistus-pvm osio]}]
    taulun-tyyppi))

(defmethod vahvista-tai-kumoa-indeksikorjaukset! :kiinteahintainen-tyo
  [_ db vahvista? {:keys [urakka-id hoitovuosi-nro hoitokauden-alkupvm hoitokauden-loppupvm vahvistaja vahvistus-pvm]}]
  (log/debug "vahvista-tai-kumoa-indeksikorjaukset! :kiinteahintainen-tyo"
    [urakka-id hoitovuosi-nro hoitokauden-alkupvm hoitokauden-loppupvm vahvistaja vahvistus-pvm])

  ;; NOTE: Ainoastaan Hankintakustannukset-osiosta tulee tällä hetkellä kiinteähintaisia töitä.
  (let [rivit (q/vahvista-tai-kumoa-indeksikorjaukset-kiinteahintaisille-toille! db
                {:urakka-id urakka-id
                 :alkupvm hoitokauden-alkupvm
                 :loppupvm hoitokauden-loppupvm
                 :vahvista? vahvista?
                 :vahvistaja vahvistaja
                 :vahvistus-pvm vahvistus-pvm})]
    (log/debug "vahvista-tai-kumoa-indeksikorjaukset! :kiinteahintainen-tyo: "
      (str (if vahvista? "vahvistettuja" "kumottuja") " rivejä: ") rivit)))

(defmethod vahvista-tai-kumoa-indeksikorjaukset! :kustannusarvioitu-tyo
  [_ db vahvista? {:keys [urakka-id hoitovuosi-nro hoitokauden-alkupvm hoitokauden-loppupvm vahvistaja vahvistus-pvm
                          osio-str]}]
  (log/debug "vahvista-tai-kumoa-indeksikorjaukset! :kustannusarvioitu-tyo"
    [urakka-id hoitovuosi-nro hoitokauden-alkupvm hoitokauden-loppupvm vahvistaja vahvistus-pvm osio-str])

  (let [rivit (q/vahvista-tai-kumoa-indeksikorjaukset-kustannusarvioiduille-toille! db
                {:urakka-id urakka-id
                 :alkupvm hoitokauden-alkupvm
                 :loppupvm hoitokauden-loppupvm
                 :osio osio-str
                 :vahvista? vahvista?
                 :vahvistaja vahvistaja
                 :vahvistus-pvm vahvistus-pvm})]
    (log/debug "vahvista-tai-kumoa-indeksikorjaukset! :kustannusarvioitu-tyo: "
      (str (if vahvista? "vahvistettuja" "kumottuja") " rivejä: ") " rivejä: " rivit)))

(defmethod vahvista-tai-kumoa-indeksikorjaukset! :johto-ja-hallintokorvaus
  [_ db vahvista? {:keys [urakka-id hoitovuosi-nro hoitokauden-alkupvm hoitokauden-loppupvm vahvistaja vahvistus-pvm]}]

  (log/debug "vahvista-tai-kumoa-indeksikorjaukset! :johto-ja-hallintokorvaus"
    [urakka-id hoitovuosi-nro hoitokauden-alkupvm hoitokauden-loppupvm vahvistaja vahvistus-pvm])

  ;; NOTE: Ainoastaan Johto- ja hallintokorvaus-osiosta tulee tällä hetkellä jh-korvauksia.
  (let [rivit (q/vahvista-tai-kumoa-indeksikorjaukset-jh-korvauksille! db
                {:urakka-id urakka-id
                 :alkupvm hoitokauden-alkupvm
                 :loppupvm hoitokauden-loppupvm
                 :vahvista? vahvista?
                 :vahvistaja vahvistaja
                 :vahvistus-pvm vahvistus-pvm})]
    (log/debug "vahvista-tai-kumoa-indeksikorjaukset! :johto-ja-hallintokorvaus: "
      (str (if vahvista? "vahvistettuja" "kumottuja") " rivejä: ") " rivejä: " rivit)))

(defmethod vahvista-tai-kumoa-indeksikorjaukset! :urakka-tavoite
  [_ db vahvista? {:keys [urakka-id hoitovuosi-nro vahvistaja vahvistus-pvm]}]

  (log/debug "vahvista-tai-kumoa-indeksikorjaukset! :urakka-tavoite"
    [urakka-id hoitovuosi-nro vahvistaja vahvistus-pvm])

  (let [rivit (q/vahvista-tai-kumoa-indeksikorjaukset-urakan-tavoitteille! db
                {:urakka-id urakka-id
                 :hoitovuosi-nro hoitovuosi-nro
                 :vahvista? vahvista?
                 :vahvistaja vahvistaja
                 :vahvistus-pvm vahvistus-pvm})]
    (log/debug "vahvista-tai-kumoa-indeksikorjaukset! :urakka-tavoite: "
      (str (if vahvista? "vahvistettuja" "kumottuja") " rivejä: ") " rivejä: " rivit)))


(defn vahvista-tai-kumoa-osion-indeksikorjaukset! [db user urakka-id hoitovuosi-nro osio vahvista?]
  (let [osio-str (mhu/osio-kw->osio-str osio)
        {urakan-alkupvm ::ur/alkupvm} (first (fetch db
                                               ::ur/urakka
                                               #{::ur/alkupvm ::ur/loppupvm}
                                               {::ur/id urakka-id}))
        urakan-alkuvuosi (-> urakan-alkupvm pvm/joda-timeksi pvm/suomen-aikavyohykkeeseen pvm/vuosi)
        {:keys [alkupvm loppupvm]} (pvm/mhu-hoitovuoden-nro->hoitokauden-aikavali urakan-alkuvuosi hoitovuosi-nro)
        taulutyypit (mhu/suunnitelman-osio->taulutyypit osio)]

    (log/debug "Yritetään" (if vahvista? "vahvistaa" "kumota") "osion:" osio
      "rivit taulutyypeille: " taulutyypit " hoitokaudelle: " [alkupvm loppupvm])

    ;; Yritä vahvistaa osioon liittyvien summarivien indeksikorjaukset
    ;; Käydään läpi kaikki osioon liittyvät taulutyypit ja vahvistetaan vahvistamattomat rivit.
    (when (seq taulutyypit)
      (doseq [t taulutyypit]
        (vahvista-tai-kumoa-indeksikorjaukset! t db vahvista?
          {:urakka-id urakka-id
           ;; FIXME: "hoitovuosi" onkin oikeasti hoitovuosi-nro, eli järjestysnumero eikä vuosi!
           :hoitovuosi-nro hoitovuosi-nro
           :hoitokauden-alkupvm alkupvm
           :hoitokauden-loppupvm loppupvm
           :vahvistaja (:id user)
           :vahvistus-pvm (pvm/nyt)
           :osio-str osio-str})))))

;; |---- Indeksikorjaukset END ----


(defn hae-urakan-tavoite
  [db user {:keys [urakka-id]}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu user urakka-id)
  (q/hae-budjettitavoite db {:urakka urakka-id}))


(defn- redusoi-tilat
  [tilat tila]
  (let [{:keys [hoitovuosi osio vahvistettu]} tila
        osio (keyword osio)]
    (assoc-in tilat [osio hoitovuosi] vahvistettu)))

(defn hae-urakan-suunnitelman-tilat
  [db user {:keys [urakka-id]}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu user urakka-id)
  (let [tilat (q/hae-suunnitelman-tilat db {:urakka urakka-id})
        tilat (reduce redusoi-tilat {} tilat)]
    tilat))



(defn vahvista-suunnitelman-osa-hoitovuodelle
  "Merkataan vahvistus ja lasketaan indeksikorjatut luvut. Vahvistus tehdään osissa, joten lasketaan indeksikorjatut luvutkin osissa?"
  ;; FIXME: Avain "tyyppi" on oikeasti osio
  ;; FIXME: Avain "hoitovuosi" onkin oikeasti hoitovuosi-nro, eli järjestysnumero eikä vuosi!
  [db user {:keys [urakka-id hoitovuosi tyyppi]}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu user urakka-id)

  (let [osio (mhu/osio-kw->osio-str tyyppi)]
    (jdbc/with-db-transaction [db db]
      ;; FIXME: Avain "tyyppi" on oikeasti osio
      ;; Vahvista osion taulurivit osioon liittyvistä tietokantatauluista.
      (vahvista-tai-kumoa-osion-indeksikorjaukset! db user urakka-id hoitovuosi tyyppi true)

      (let [hakuparametrit {:urakka urakka-id
                            ;; FIXME: "hoitovuosi" onkin oikeasti hoitokausi, eli järjestysnumero eikä vuosi!
                            :hoitovuosi hoitovuosi
                            :osio osio
                            :muokkaaja (:id user)
                            :vahvistaja (:id user)}
            onko-osiolla-tila? (seq (q/hae-suunnitelman-osan-tila-hoitovuodelle db
                                      (dissoc hakuparametrit :muokkaaja :vahvistaja)))]
        (if onko-osiolla-tila?
          ;; Jos osiolla on jo tila, niin silloin muokataan vanhaa vahvistusta ja tallennetaan
          ;; samalla myös muokkaaja ja muokkausaika.
          (q/vahvista-suunnitelman-osa-hoitovuodelle db {:urakka urakka-id
                                                         ;; FIXME: "hoitovuosi" onkin oikeasti hoitokausi, eli järjestysnumero eikä vuosi!
                                                         :hoitovuosi hoitovuosi
                                                         :osio osio
                                                         :muokkaaja (:id user)
                                                         :vahvistaja (:id user)})
          ;; Jos osiolla ei ole vielä tila-riviä, niin luodaan se ja tallennetaan samalla
          ;; tarvittavat vahvistustiedot.
          (q/lisaa-suunnitelmalle-tila db {:urakka urakka-id
                                           ;; FIXME: "hoitovuosi" onkin oikeasti hoitokausi, eli järjestysnumero eikä vuosi!
                                           :hoitovuosi hoitovuosi
                                           :osio osio
                                           :luoja (:id user)
                                           :vahvistaja (:id user)
                                           :vahvistettu true
                                           :vahvistus_pvm (pvm/nyt)}))
        (hae-urakan-suunnitelman-tilat db user {:urakka-id urakka-id})))))

(defn kumoa-suunnitelman-osan-vahvistus-hoitovuodelle
  ;; FIXME: Avain "tyyppi" on oikeasti osio-kw
  [db user {:keys [urakka-id hoitovuosi tyyppi]}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu user urakka-id)

  (jdbc/with-db-transaction [db db]
    (let [kumottavat-osiot (into #{tyyppi}
                             (:kumoa-osiot (mhu/osioiden-riippuvuudet tyyppi)))]
      (log/debug "Kumotaan vahvistus osioille: " kumottavat-osiot)

      (doseq [osio kumottavat-osiot]

        ;; Kumoa vahvistukset osioon liittyvistä tietokantatauluista.
        (vahvista-tai-kumoa-osion-indeksikorjaukset! db user urakka-id hoitovuosi osio false)

        (let [hakuparametrit {:urakka urakka-id
                              :hoitovuosi hoitovuosi
                              :osio (mhu/osio-kw->osio-str osio)}
              onko-osiolla-tila? (seq (q/hae-suunnitelman-osan-tila-hoitovuodelle db hakuparametrit))]

          (when onko-osiolla-tila?
            (q/kumoa-suunnitelman-osan-vahvistus-hoitovuodelle db {:urakka urakka-id
                                                                   :hoitovuosi hoitovuosi
                                                                   :osio (mhu/osio-kw->osio-str osio)
                                                                   :muokkaaja (:id user)})))))

    (hae-urakan-suunnitelman-tilat db user {:urakka-id urakka-id})))


;; NOTE: Tätä käytetään ilmeisesti lähinnä siihen, että osion lukujen tallentamisen yhteydessä
;;   mhu-kustannussuunnitelma.cljs:ssä tarkastetaan onko osiolla jo tila. Jos ei ole, niin tätä palvelua
;;   kutsutaan ja luodaan osiolle tilarivi halutulle hoitovuodelle/vuosille.
(defn tallenna-suunnitelman-osalle-tila
  [db user {:keys [urakka-id hoitovuodet tyyppi]}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu user urakka-id)
  (jdbc/with-db-transaction
    [db db]
    (doseq [hv hoitovuodet]
      (q/lisaa-suunnitelmalle-tila db {:urakka urakka-id
                                       :hoitovuosi hv
                                       :osio (mhu/osio-kw->osio-str tyyppi)
                                       :vahvistaja nil
                                       :luoja (:id user)
                                       :vahvistettu false
                                       :vahvistus_pvm nil}))
    (hae-urakan-suunnitelman-tilat db user {:urakka-id urakka-id})))


(defn tallenna-suunnitelman-muutos
  [db user {:keys [selite muutoksen-syy urakka-id tyo tyon-tyyppi vuosi] :as muutos}]
  ;; TODO: Tehdään tämä loppuun sen jälkeen kun osiota vahvistaessa indeksikorjatut luvut saadaan kantaan talteen.
  ;; TODO: Vie loppuun. Tämä ei tee vielä mitään.

  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu user urakka-id)
  (let [tiedot {tyon-tyyppi tyo
                :urakka urakka-id
                :selite selite
                :kuvaus muutoksen-syy
                :luoja (:id user)}]))

(defn hae-urakan-indeksikertoimet
  [db user {:keys [urakka-id]}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu user urakka-id)
  (jdbc/with-db-transaction [db db]
                            (let [pyorista #(/ (Math/round (* %1 (Math/pow 10 %2))) (Math/pow 10 %2))
                                  {::ur/keys [alkupvm loppupvm indeksi]} (first (fetch db
                                                                                       ::ur/urakka
                                                                                       #{::ur/alkupvm ::ur/loppupvm ::ur/indeksi}
                                                                                       {::ur/id urakka-id}))
                                  urakan-alkuvuosi (-> alkupvm pvm/joda-timeksi pvm/suomen-aikavyohykkeeseen pvm/vuosi)
                                  urakan-loppuvuosi (-> loppupvm pvm/joda-timeksi pvm/suomen-aikavyohykkeeseen pvm/vuosi)
                                  vertailu-kk-mhu (fn [urakan-akuvuosi]
                                                    (cond
                                                      ;; 2023 ja jälkeen alkavilla urakoilla käytetään indeksin tarkastelukuukautena elokuuta (VHAR-6948)
                                                      (>= urakan-akuvuosi 2023) 8
                                                      ;; Muihin aikoihin alkavilla urakoilla käytetään tarkastelukuukautena syyskuuta
                                                      :else 9))
                                  perusluku (:perusluku (first (i-q/hae-urakan-indeksin-perusluku db {:urakka-id urakka-id})))
                                  indeksiluvut-urakan-aikana (sequence
                                                               (comp (filter (fn [{:keys [kuukausi vuosi]}]
                                                                               (and (= (vertailu-kk-mhu urakan-alkuvuosi) kuukausi)
                                                                                    (>= vuosi urakan-alkuvuosi))))
                                                                     (remove (fn [{:keys [vuosi]}]
                                                                               (>= vuosi urakan-loppuvuosi)))
                                                                     (map (fn [{:keys [arvo vuosi]}]
                                                                            {:vuosi vuosi
                                                                             ;; Halutaan numero kolmen desimaalin tarkkuudella. Pelataan sen varaan, että indeksikerroin
                                                                             ;; Ei nouse yli kymmenen, jolloin with-precision 4 riittää.
                                                                             ;; Ratkaisu pyöristää indeksikerrointa. Tämä on sovittu käytäntö ELYissä ja perustuu myös siihen,
                                                                             ;; että tilastokeskus ilmaisee indeksikertoimen kolmella desimaalilla (prosentin kymmenyksen tarkkuudella).
                                                                             :indeksikerroin (pyorista (with-precision 4 (/ arvo perusluku)) 3)})))
                                                               (i-q/hae-indeksi db {:nimi indeksi}))
                                  urakan-indeksien-maara (count indeksiluvut-urakan-aikana)]
                              (if (= 5 urakan-indeksien-maara)
                                (vec indeksiluvut-urakan-aikana)
                                (mapv (fn [index]
                                        (if (empty? indeksiluvut-urakan-aikana)
                                          ;; VHAR-5334: Palautetaan nil indeksikertoimeksi urakoille, jotka eivät ole vielä alkaneet.
                                          nil
                                          ;; VHAR-5334: Palautetaan indeksit vain hoitovuosille, joilla on indeksejä.
                                          ;; Lopuille hoitovuosille nil.
                                          (nth indeksiluvut-urakan-aikana index nil)))
                                      (range 0 5))))))

(defn tallenna-urakan-tavoite
  "Palvelu joka tallentaa urakan budjettiin liittyvät tavoitteet: tavoitehinta, kattohinta ja edelliseltä hoitovuodelta siirretty tavoitehinnan lisä/vähennys.
  Budjettitiedoissa: hoitokausi, tavoitehinta, kattohinta.
  Budjettitavoitteet-vektorissa voi lähettää yhden tai useamman mäpin, jossa kussakin urakan yhden hoitokauden tiedot."
  [db user {:keys [urakka-id tavoitteet]}]

  (let [urakkatyyppi (keyword (:tyyppi (first (urakat-q/hae-urakan-tyyppi db urakka-id))))]
    (if-not (= urakkatyyppi :teiden-hoito)
      (throw (IllegalArgumentException. (str "Urakka " urakka-id " on tyyppiä " urakkatyyppi ". Tavoite kirjataan vain teiden hoidon urakoille."))))
    (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu user urakka-id))

  (assert (vector? tavoitteet) "tavoitteet tulee olla vektori")

  (let [ylitetyt-kattohinnat (remove nil? (map
                                            (fn [{:keys [hoitokausi tavoitehinta kattohinta]}]
                                              (when (and kattohinta tavoitehinta
                                                      ;; Sallitaan kattohinnan arvoksi nolla mikäli tavoitehintakin on nolla.
                                                      (not (and
                                                             (zero? kattohinta)
                                                             (zero? tavoitehinta)))
                                                      (<= kattohinta tavoitehinta))
                                                hoitokausi))
                                            tavoitteet))]
    (when (seq ylitetyt-kattohinnat)
      (throw (IllegalArgumentException. (str "Tavoitehinta on suurempi tai yhtäsuuri kuin kattohinta "
                                          (if (< 1 (count ylitetyt-kattohinnat))
                                            (str "hoitokausilla " (string/join ", " ylitetyt-kattohinnat))
                                            (str "hoitokaudella " (first ylitetyt-kattohinnat))))))))

  (jdbc/with-db-transaction [c db]
                            (let [urakan-indeksit (hae-urakan-indeksikertoimet db user {:urakka-id urakka-id})
                                  tavoitteet-kannassa (q/hae-budjettitavoite c {:urakka urakka-id})
                                  tallennettavat-hoitokaudet (into #{} (map :hoitokausi tavoitteet))
                                  paivitettavat-tavoitteet (into #{}
                                                                 (map :hoitokausi)
                                                                 (filter #(tallennettavat-hoitokaudet (:hoitokausi %)) tavoitteet-kannassa))]
                              (doseq [hoitokausitavoite tavoitteet]
                                (as-> hoitokausitavoite hkt
                                  (assoc hkt
                                    :urakka urakka-id
                                    :kayttaja (:id user)
                                    :tavoitehinta-indeksikorjattu
                                    (indeksikorjaa (indeksikerroin urakan-indeksit (:hoitokausi hkt))
                                      (:tavoitehinta hkt))
                                    :kattohinta (:kattohinta hkt)
                                    :kattohinta-indeksikorjattu
                                    (indeksikorjaa (indeksikerroin urakan-indeksit (:hoitokausi hkt))
                                      (:kattohinta hkt)))

                                  (if (not (paivitettavat-tavoitteet (:hoitokausi hkt)))
                                        (q/tallenna-budjettitavoite<! c hkt)
                                        (q/paivita-budjettitavoite<! c hkt))))
                              {:onnistui? true})))

(defn hae-urakan-kiinteahintaiset-tyot [db user urakka-id]
  (let [kiinteahintaiset-tyot (kiinthint-tyot/hae-urakan-kiinteahintaiset-tyot db user urakka-id)]
    (map (fn [tyo]
           (-> tyo
               (assoc :toimenpide-avain (mhu/toimenpide->toimenpide-avain (:toimenpiteen-koodi tyo)))
               (dissoc :toimenpideinstanssi :toimenpiteen-koodi)))
         kiinteahintaiset-tyot)))

(defn hae-urakan-kustannusarvoidut-tyot
  [db user urakka-id]
  (let [kustannusarvoidut-tyot (kustarv-tyot/hae-urakan-kustannusarvoidut-tyot-nimineen db user urakka-id)]
    (map (fn [tyo]
           (-> tyo
               (assoc :toimenpide-avain (mhu/toimenpide->toimenpide-avain (:toimenpiteen-koodi tyo)))
               (assoc :haettu-asia (or (mhu/tehtava->tallennettava-asia (:tehtavan-tunniste tyo))
                                       (mhu/tehtavaryhma->tallennettava-asia (:tehtavaryhman-tunniste tyo))))
               (dissoc :toimenpiteen-koodi :tehtavan-tunniste :tehtavaryhman-tunniste)))
         kustannusarvoidut-tyot)))

(defn urakan-johto-ja-hallintokorvausten-datan-rikastaminen [data urakan-alkuvuosi]
  (let [to-float #(when % (float %))
        johto-ja-hallintokorvaukset (map (fn [johto-ja-hallintokorvaus]
                                           (-> johto-ja-hallintokorvaus
                                               (update :tunnit to-float)
                                               (update :tuntipalkka to-float)
                                               (update :maksukuukaudet konv/pgarray->vector)))
                                         data)
        hoitokauden-numero-lisatty (apply concat
                                          (map-indexed (fn [index [_ tiedot]]
                                                         (map (fn [data]
                                                                (if (:ennen-urakkaa data)
                                                                  (assoc data :hoitokausi 0)
                                                                  (assoc data :hoitokausi (inc index))))
                                                              tiedot))
                                                       (into (sorted-map)
                                                             (group-by #(pvm/paivamaaran-hoitokausi (pvm/luo-pvm-dec-kk (:vuosi %) (:kuukausi %) 15))
                                                                       johto-ja-hallintokorvaukset))))
        maksukausi-lisatty (reduce (fn [johto-ja-hallintokorvaukset {:keys [toimenkuva kuukausi ennen-urakkaa] :as johto-ja-hallintokorvaus}]
                                     (cond
                                       ;; Ennen urakkakautta tapahtuvat kustannukset toimivat kaikkina vuosina samoin
                                       ennen-urakkaa (conj johto-ja-hallintokorvaukset (assoc johto-ja-hallintokorvaus :maksukausi nil))
                                       ;; Vain vuonna 2021 ja sitä aiemmin alkaneille urakoille voi määritellä toimenkuvakohtaisesti erilaisia kuukausijaksoja
                                       (and (not (bs-p/vuosikohtaiset-toimenkuvat? urakan-alkuvuosi))
                                         (or (= toimenkuva "päätoiminen apulainen")
                                           (= toimenkuva "apulainen/työnjohtaja"))) (if (<= 5 kuukausi 9)
                                                                                      (conj johto-ja-hallintokorvaukset (assoc johto-ja-hallintokorvaus :maksukausi :kesa))
                                                                                      (conj johto-ja-hallintokorvaukset (assoc johto-ja-hallintokorvaus :maksukausi :talvi)))
                                       :else (conj johto-ja-hallintokorvaukset (assoc johto-ja-hallintokorvaus :maksukausi :molemmat))))
                                   [] hoitokauden-numero-lisatty)
        ;; Vuoden -22 ja sen jälkeen alkavilla urakoilla ei ole enää erikseen maksukausia ja kk-v erottelua ei enää tarvita
        kk-v-lisatty (if (not (bs-p/vuosikohtaiset-toimenkuvat? urakan-alkuvuosi))
                       (map (fn [{:keys [toimenkuva maksukausi hoitokausi] :as johto-ja-hallintokorvaus}]
                              (cond
                                (= :kesa maksukausi) (assoc johto-ja-hallintokorvaus :kk-v 5)
                                (= :talvi maksukausi) (assoc johto-ja-hallintokorvaus :kk-v 7)
                                (and (= toimenkuva "hankintavastaava") (= 0 hoitokausi)) (assoc johto-ja-hallintokorvaus :kk-v 4.5)
                                (= toimenkuva "viherhoidosta vastaava henkilö") (assoc johto-ja-hallintokorvaus :kk-v 5)
                                (= toimenkuva "harjoittelija") (assoc johto-ja-hallintokorvaus :kk-v 4)
                                :else (assoc johto-ja-hallintokorvaus :kk-v 12)))
                         maksukausi-lisatty)
                       maksukausi-lisatty)]
    kk-v-lisatty))

(defn hae-urakan-johto-ja-hallintokorvaukset [db urakka-id]
  (urakan-johto-ja-hallintokorvausten-datan-rikastaminen
    (q/hae-johto-ja-hallintokorvaukset db {:urakka-id urakka-id})
    (urakat-q/hae-urakan-alkuvuosi db urakka-id)))

(defn hae-urakan-omat-johto-ja-hallintokorvaukset
  "Hae JH-korvaukset itse lisätyille toimenkuville."
  [db urakka-id]
  (urakan-johto-ja-hallintokorvausten-datan-rikastaminen
    (q/hae-omat-johto-ja-hallintokorvaukset db {:urakka-id urakka-id})
    (urakat-q/hae-urakan-alkuvuosi db urakka-id)))

(defn hae-ja-rikasta-urakan-johto-ja-hallintokorvaukset! [db urakka-id]
  (jdbc/with-db-transaction [db db]
    (let [jh-korvaukset (hae-urakan-johto-ja-hallintokorvaukset db urakka-id)
          omat-jh-korvaukset (hae-urakan-omat-johto-ja-hallintokorvaukset db urakka-id)
          urakan-omat-jh-toimenkuvat (q/hae-urakan-omat-jh-toimenkuvat db {:urakka-id urakka-id})
          jhk-omiariveja-lkm 2
          luotujen-toimenkuvien-idt (when (> jhk-omiariveja-lkm (count urakan-omat-jh-toimenkuvat))
                                      (for [_ (range 0 (- jhk-omiariveja-lkm (count urakan-omat-jh-toimenkuvat)))]
                                        (:id (q/lisaa-oma-johto-ja-hallintokorvaus-toimenkuva<! db
                                               {:toimenkuva nil :urakka-id urakka-id}))))]
      {:vakiot jh-korvaukset
       :omat omat-jh-korvaukset
       :omat-toimenkuvat (vec (concat urakan-omat-jh-toimenkuvat
                                (map #(identity {:toimenkuva-id % :toimenkuva nil})
                                  luotujen-toimenkuvien-idt)))})))

(defn hae-urakan-budjetoidut-tyot
  "Palvelu, joka palauttaa urakan budjetoidut työt.
  Palvelu palauttaa kaikki kiinteähintaiset, kustannusarvioidut ja yksikköhintaiset työt mappiin jäsenneltynä."
  [db user {:keys [urakka-id]}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu user urakka-id)

  {:kiinteahintaiset-tyot (hae-urakan-kiinteahintaiset-tyot db user urakka-id)
   :kustannusarvioidut-tyot (hae-urakan-kustannusarvoidut-tyot db user urakka-id)
   :johto-ja-hallintokorvaukset (hae-ja-rikasta-urakan-johto-ja-hallintokorvaukset! db urakka-id)})

(defn- muodosta-ajat
  "Oletuksena, että jos pelkästään vuosi on annettuna kuukauden sijasta, kyseessä on hoitokauden aloitusvuosi"
  [ajat]
  (reduce (fn [ajat {:keys [vuosi kuukausi]}]
            (if kuukausi
              (conj ajat {:vuosi vuosi
                          :kuukausi kuukausi})
              (let [ajat-valille (fn [vuosi [alku loppu]]
                                   (map #(identity
                                           {:vuosi vuosi
                                            :kuukausi %})
                                        (range alku (inc loppu))))
                    hoitokauden-vuodet [vuosi (inc vuosi)]]
                (into []
                      (concat ajat
                              (ajat-valille (first hoitokauden-vuodet) [10 12])
                              (ajat-valille (second hoitokauden-vuodet) [1 9]))))))
          [] ajat))

;; TODO: Katso, onko pvm-apufunktioissa vastaavaa apuria ja käytä sitä.
(defn- hoitovuodella?
  [vuosi kuukausi v]
  (or (and (= vuosi (inc v))
           (< kuukausi 10))
      (and (= vuosi v)
           (> kuukausi 9))))

(defn- tallenna-muutokset-suunnitelmassa
  [db user tyon-tiedot muutostiedot perustiedot hoitovuodet]
  (mapv (fn [v]
          (let [{:keys [vuosi kuukausi summa id]} tyon-tiedot
                maara (get-in muutostiedot [v :maara])]
            #_(println tyon-tiedot)
            (when (hoitovuodella? vuosi kuukausi v)
              (tallenna-suunnitelman-muutos db user (-> muutostiedot
                                                        (get v)
                                                        (assoc :tyo id
                                                               :vuosi v
                                                               :muutos (- maara summa))
                                                        (merge perustiedot))))))
        hoitovuodet))

(defn tallenna-kiinteahintaiset-tyot
  "NOTE: Kiinteahintaisia töitä tulee tällä hetkellä vain Hankintakustannukset-osiosta. Osion tunnistetta ei siis ole tarpeen
         lähettää käyttöliittymästä tietokantaan tallennettavaksi."
  [db user {:keys [urakka-id toimenpide-avain ajat summa muutos]}]
  {:pre [(integer? urakka-id)
         (keyword? toimenpide-avain)
         (or (nil? summa)
             (number? summa))]}
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu user urakka-id)

  (jdbc/with-db-transaction [db db]
    (let [urakan-indeksit (hae-urakan-indeksikertoimet db user {:urakka-id urakka-id})
          {urakan-alkupvm ::ur/alkupvm} (first (fetch db
                                                 ::ur/urakka
                                                 #{::ur/alkupvm ::ur/loppupvm ::ur/indeksi}
                                                 {::ur/id urakka-id}))
          toimenpide (mhu/toimenpide-avain->toimenpide toimenpide-avain)
          {toimenpide-id ::tpk/id} (first (fetch db ::tpk/toimenpide
                                            #{::tpk/id}
                                            {::tpk/taso 3
                                             ::tpk/koodi toimenpide}))
          {toimenpideinstanssi-id :id} (first (tpi-q/hae-urakan-toimenpideinstanssi db {:urakka urakka-id :tp toimenpide-id}))
          _ (when (nil? toimenpideinstanssi-id)
              (throw (Exception. "Toimenpideinstanssia ei löydetty")))

          ajat (muodosta-ajat ajat)
          tallenna-muutokset-hoitovuosille (keys muutos)
          olemassa-olevat-kiinteahintaiset-tyot-vuosille (fetch db ::bs/kiinteahintainen-tyo
                                                           #{::bs/id ::bs/smallint-v ::bs/smallint-kk ::bs/summa}
                                                           {::bs/smallint-v (op/in (into #{} (distinct (map :vuosi ajat))))
                                                            ::bs/toimenpideinstanssi toimenpideinstanssi-id})
          olemassa-olevat-kiinteahintaiset-tyot (filter (fn [{::bs/keys [smallint-v smallint-kk]}]
                                                          (some #(and (= (:vuosi %) smallint-v)
                                                                   (= (:kuukausi %) smallint-kk))
                                                            ajat))
                                                  olemassa-olevat-kiinteahintaiset-tyot-vuosille)
          uudet-kiinteahintaiset-tyot-ajat (remove (fn [{:keys [vuosi kuukausi]}]
                                                     (some #(and (= vuosi (::bs/smallint-v %))
                                                              (= kuukausi (::bs/smallint-kk %)))
                                                       olemassa-olevat-kiinteahintaiset-tyot))
                                             ajat)
          perusosa {:urakka-id urakka-id
                    :tyon-tyyppi :kiinteahintainen-tyo}]
      (kiin-q/merkitse-kustannussuunnitelmat-likaisiksi! db {:toimenpideinstanssi toimenpideinstanssi-id})

      #_(println olemassa-olevat-kiinteahintaiset-tyot)
      #_(println uudet-kiinteahintaiset-tyot-ajat)

      (when-not (empty? olemassa-olevat-kiinteahintaiset-tyot)
        (doseq [{vuosi ::bs/smallint-v kuukausi ::bs/smallint-kk :as olemassa-oleva-tyo} olemassa-olevat-kiinteahintaiset-tyot]
          (let [{vanha-summa ::bs/summa id ::bs/id} olemassa-oleva-tyo]
            ;; TODO: Muutosten tallentaminen ei vielä tee mitään.
            (tallenna-muutokset-suunnitelmassa db user
              {:vuosi vuosi :kuukausi kuukausi :summa vanha-summa :id id}
              muutos perusosa tallenna-muutokset-hoitovuosille))
          #_(mapv (fn [v]
                    (let [{vuosi ::bs/smallint-v kuukausi ::bs/smallint-kk summa ::bs/summa} olemassa-oleva-tyo
                          maara (get-in muutos [v :maara])]
                      (println olemassa-oleva-tyo)
                      (when
                        (hoitovuodella? vuosi kuukausi v)
                        (tallenna-suunnitelman-muutos db user (-> muutos
                                                                (get v)
                                                                (assoc :tyo (::bs/id olemassa-oleva-tyo)
                                                                       :vuosi v
                                                                       :muutos (- maara summa))
                                                                (merge perusosa))))))
              tallenna-muutokset-hoitovuosille)
          (update! db ::bs/kiinteahintainen-tyo
            {::bs/summa summa
             ::bs/summa-indeksikorjattu (indeksikorjaa
                                          (indeksikerroin urakan-indeksit
                                            (pvm/paivamaara->mhu-hoitovuosi-nro
                                              urakan-alkupvm (pvm/luo-pvm-dec-kk
                                                               vuosi
                                                               kuukausi 1)))
                                          summa)
             ::bs/muokattu (pvm/nyt)
             ::bs/muokkaaja (:id user)}
            {::bs/id (::bs/id olemassa-oleva-tyo)})))

      (when-not (empty? uudet-kiinteahintaiset-tyot-ajat)
        (let [paasopimus (urakat-q/urakan-paasopimus-id db urakka-id)]
          (doseq [{:keys [vuosi kuukausi]} uudet-kiinteahintaiset-tyot-ajat]
            (let [uusi-rivi (insert! db ::bs/kiinteahintainen-tyo
                              {::bs/smallint-v vuosi
                               ::bs/smallint-kk kuukausi
                               ::bs/summa summa
                               ::bs/summa-indeksikorjattu (indeksikorjaa
                                                            (indeksikerroin urakan-indeksit
                                                              (pvm/paivamaara->mhu-hoitovuosi-nro
                                                                urakan-alkupvm (pvm/luo-pvm-dec-kk vuosi kuukausi 1)))
                                                            summa)
                               ::bs/toimenpideinstanssi toimenpideinstanssi-id
                               ::bs/sopimus paasopimus
                               ::bs/luotu (pvm/nyt)
                               ::bs/luoja (:id user)})]
              #_(println "uusi rivi " uusi-rivi)

              ;; TODO: Muutosten tallentaminen ei vielä tee mitään.
              (tallenna-muutokset-suunnitelmassa db user
                {:vuosi vuosi :kuukausi kuukausi :summa summa :id (::bs/id uusi-rivi)}
                muutos perusosa tallenna-muutokset-hoitovuosille)
              #_(mapv (fn [v]
                        (when (hoitovuodella? vuosi kuukausi v)
                          (tallenna-suunnitelman-muutos db user (-> muutos
                                                                  (get v)
                                                                  (assoc :tyo (::bs/id uusi-rivi)
                                                                         :vuosi v
                                                                         :muutos (get-in muutos [v :maara]))
                                                                  (merge perusosa)))))
                  tallenna-muutokset-hoitovuosille)))))
      {:onnistui? true
       :kiinteahintaiset-tyot (hae-urakan-kiinteahintaiset-tyot db user urakka-id)})))


(defn tallenna-johto-ja-hallintokorvaukset
  "Tallentaa johto- ja hallintokorvaukset johto_ja_hallintokorvaus tauluun ja toimenkuvat johto_ja_hallintokorvaus_toimenkuva-tauluun.

  NOTE: Johto- ja hallintokorvaukset sisältää vain yhdestä osiosta tulevaa dataa ja se tallennetaan vain yhteen tauluun.
        Toistaiseksi ei ole siis tarpeen tarkkailla mistä osiosta data on relevanttiin tauluun tallennettu."
  ;;TODO: Tätä kannattaisi refaktoroida.
  [db user {:keys [urakka-id toimenkuva toimenkuva-id ennen-urakkaa? jhk-tiedot maksukausi muutos]}]
  {:pre [(integer? urakka-id)
         (or (and toimenkuva-id (integer? toimenkuva-id))
           (and toimenkuva (string? toimenkuva)))]}

  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu user urakka-id)

  (jdbc/with-db-transaction [db db]
    (let [urakan-indeksit (hae-urakan-indeksikertoimet db user {:urakka-id urakka-id})
          {urakan-alkupvm ::ur/alkupvm} (first (fetch db
                                                 ::ur/urakka
                                                 #{::ur/alkupvm ::ur/loppupvm ::ur/indeksi}
                                                 {::ur/id urakka-id}))
          urakan-alkuvuosi (urakat-q/hae-urakan-alkuvuosi db urakka-id)
          toimenkuva-id (or toimenkuva-id
                          (::bs/id (first (fetch db ::bs/johto-ja-hallintokorvaus-toimenkuva
                                            #{::bs/id}
                                            {::bs/toimenkuva toimenkuva}))))
          maksukuukaudet (::bs/maksukuukaudet (first (fetch db ::bs/johto-ja-hallintokorvaus-toimenkuva
                                                       #{::bs/maksukuukaudet}
                                                       {::bs/id toimenkuva-id})))
          toimenkuvan-urakka-id (::bs/urakka-id (first (fetch db ::bs/johto-ja-hallintokorvaus-toimenkuva
                                                         #{::bs/urakka-id}
                                                         {::bs/id toimenkuva-id})))
          _ (when-not (or (nil? toimenkuvan-urakka-id) (= urakka-id toimenkuvan-urakka-id))
              (throw (Exception. "Yritetään tallentaa toisen urakan toimenkuvalle")))
          _ (when (nil? toimenkuva-id)
              (throw (Exception. (str "Annettu toimenkuva ei löydy kannasta!\n Toimenkuva: " toimenkuva))))
          toimenpideinstanssi-id (:id (first
                                        (tpi-q/hae-urakan-toimenpideinstanssi-toimenpidekoodilla db
                                          {:urakka urakka-id
                                           :koodi (mhu/toimenpide-avain->toimenpide :mhu-johto)})))
          tallenna-muutokset-hoitovuosille (keys muutos)
          olemassa-olevat-jhkt (fetch db ::bs/johto-ja-hallintokorvaus
                                 #{::bs/id ::bs/toimenkuva-id ::bs/vuosi ::bs/kuukausi
                                   ::bs/ennen-urakkaa ::bs/tunnit ::bs/tuntipalkka}
                                 {::bs/urakka-id urakka-id
                                  ::bs/toimenkuva-id toimenkuva-id
                                  ::bs/vuosi (op/in (distinct (map :vuosi jhk-tiedot)))
                                  ::bs/ennen-urakkaa ennen-urakkaa?})
          olemassa-olevat-jhkt (filter (fn [{::bs/keys [vuosi kuukausi] :as jhk}]
                                         (some #(when (and (= vuosi (:vuosi %))
                                                        (= kuukausi (:kuukausi %)))
                                                  jhk)
                                           jhk-tiedot))
                                 olemassa-olevat-jhkt)
          uudet-jhkt (remove (fn [{:keys [vuosi kuukausi]}]
                               (some #(and (= vuosi (::bs/vuosi %))
                                        (= kuukausi (::bs/kuukausi %)))
                                 olemassa-olevat-jhkt))
                       jhk-tiedot)
          perusosa {:urakka-id urakka-id
                    :tyon-tyyppi :johto-ja-hallintokorvaus}]
      (ka-q/merkitse-kustannussuunnitelmat-likaisiksi! db {:toimenpideinstanssi toimenpideinstanssi-id})
      (kiin-q/merkitse-maksuerat-likaisiksi-hoidonjohdossa! db {:toimenpideinstanssi toimenpideinstanssi-id})

      ;; Päivitä toimenkuva
      #_(println "### Maksukausi: " maksukausi)
      #_(println "### Maksukuukaudet: " maksukuukaudet ", maksukausi->kuukaudet: " (mhu/maksukausi->kuukaudet-range maksukausi))
      (when (and maksukausi
              (not (= (mhu/maksukausi->kuukaudet-range maksukausi)
                     maksukuukaudet)))
        (update! db
          ::bs/johto-ja-hallintokorvaus-toimenkuva
          {::bs/maksukuukaudet (mhu/maksukausi->kuukaudet-range maksukausi)}
          {::bs/id toimenkuva-id}))

      ;; Käsittele päivitettävät jhk:t
      (when-not (empty? olemassa-olevat-jhkt)
        (doseq [jhk olemassa-olevat-jhkt
                :let [tunnit (some (fn [{:keys [vuosi kuukausi tunnit]}]
                                     (let [tunnit (if (bs-p/vuosikohtaiset-toimenkuvat? urakan-alkuvuosi)
                                                    1       ;; Kaikissa -22 tai myöhemmin alkaneissa urakoissa käytetään kokonaishintaa. Yksittäistä tuntia ei enää tallenneta
                                                    tunnit)]

                                       (when (and (= vuosi (::bs/vuosi jhk))
                                               (= kuukausi (::bs/kuukausi jhk)))
                                         tunnit)))
                               jhk-tiedot)
                      tuntipalkka (some (fn [{:keys [vuosi kuukausi tuntipalkka]}]
                                          (when (and (= vuosi (::bs/vuosi jhk))
                                                  (= kuukausi (::bs/kuukausi jhk)))
                                            tuntipalkka))
                                    jhk-tiedot)]]
          ;; TODO: Varo tätä! Tässä on identtinen symboli tuntipalkalle ja tunneille kuin yllä, kun muutoksia aletaan käsittelemään alla letissä.
          ;;       Kokonaisuutena tämä jhk-tiedot ja jhk mappien pyörittely on tosi sekavaa. Refaktoroi.
          (let [{vuosi ::bs/smallint-v kuukausi ::bs/smallint-kk id
                 ::bs/id tunnit ::bs/tunnit tuntipalkka ::bs/tuntipalkka} jhk
                summa (if (and tunnit tuntipalkka)
                        (* tunnit tuntipalkka)
                        0)]

            ;; TODO: Muutosten tallentaminen ei vielä tee mitään.
            (tallenna-muutokset-suunnitelmassa db user
              {:vuosi vuosi :kuukausi kuukausi :summa summa :id id}
              muutos perusosa tallenna-muutokset-hoitovuosille))

          (update! db
            ::bs/johto-ja-hallintokorvaus
            {::bs/tunnit tunnit
             ::bs/tuntipalkka tuntipalkka
             ::bs/tuntipalkka-indeksikorjattu (when tuntipalkka
                                                (indeksikorjaa
                                                  (indeksikerroin urakan-indeksit
                                                    (pvm/paivamaara->mhu-hoitovuosi-nro
                                                      urakan-alkupvm (pvm/luo-pvm-dec-kk (::bs/vuosi jhk) (::bs/kuukausi jhk) 1)))
                                                  tuntipalkka))
             ::bs/muokattu (pvm/nyt)
             ::bs/muokkaaja (:id user)}
            {::bs/id (::bs/id jhk)})))

      ;; Käsittele uudet insertoitavat jhk:t
      (when-not (empty? uudet-jhkt)
        (doseq [{:keys [vuosi kuukausi osa-kuukaudesta tunnit tuntipalkka]} uudet-jhkt]
          (let [tunnit (if (bs-p/vuosikohtaiset-toimenkuvat? urakan-alkuvuosi)
                         1 ;; Kaikissa -22 tai myöhemmin alkaneissa urakoissa käytetään kokonaishintaa. Yksittäistä tuntia ei enää tallenneta
                         tunnit)
                osa-kuukaudesta (if (bs-p/vuosikohtaiset-toimenkuvat? urakan-alkuvuosi)
                         1 ;; Kaikissa -22 tai myöhemmin alkaneissa urakoissa käytetään kokonaishintaa, joten osa-kuukaudesta on aina 1
                         osa-kuukaudesta)
                uusi-rivi (insert! db
                            ::bs/johto-ja-hallintokorvaus
                            {::bs/urakka-id urakka-id
                             ::bs/toimenkuva-id toimenkuva-id
                             ::bs/tunnit tunnit
                             ::bs/tuntipalkka tuntipalkka
                             ::bs/tuntipalkka-indeksikorjattu (when tuntipalkka
                                                                (indeksikorjaa
                                                                  (indeksikerroin urakan-indeksit
                                                                    (pvm/paivamaara->mhu-hoitovuosi-nro
                                                                      urakan-alkupvm (pvm/luo-pvm-dec-kk vuosi kuukausi 1)))
                                                                  tuntipalkka))
                             ::bs/kuukausi kuukausi
                             ::bs/vuosi vuosi
                             ::bs/ennen-urakkaa ennen-urakkaa?
                             ::bs/osa-kuukaudesta osa-kuukaudesta
                             ::bs/luotu (pvm/nyt)
                             ::bs/luoja (:id user)})
                summa (if (and tunnit tuntipalkka)
                        (* tunnit tuntipalkka)
                        0)]

            ;; TODO: Muutosten tallentaminen ei vielä tee mitään.
            (tallenna-muutokset-suunnitelmassa db user
              {:vuosi vuosi :kuukausi kuukausi :summa summa :id (::bs/id uusi-rivi)}
              muutos perusosa tallenna-muutokset-hoitovuosille))))

      {:onnistui? true
       :johto-ja-hallintokorvaukset (hae-ja-rikasta-urakan-johto-ja-hallintokorvaukset! db urakka-id)})))

(defn tallenna-kustannusarvioitu-tyo!
  [db user {:keys [osio toteumatyyppi tehtava tehtavaryhma toimenpide urakka-id ajat summa indeksikorjaa? muutos]}]
  {:pre [(keyword? osio)
         (string? toteumatyyppi)
         (string? toimenpide)
         (integer? urakka-id)
         (sequential? ajat)]}
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu user urakka-id)

  (jdbc/with-db-transaction [db db]
    (let [osio-str (mhu/osio-kw->osio-str osio) ;; Kustannussuunnitelman osio, josta arvo on lähetetty tallennettavaksi.
          urakan-indeksit (hae-urakan-indeksikertoimet db user {:urakka-id urakka-id})
          {urakan-alkupvm ::ur/alkupvm} (first (fetch db
                                                 ::ur/urakka
                                                 #{::ur/alkupvm ::ur/loppupvm ::ur/indeksi}
                                                 {::ur/id urakka-id}))
          {tehtava-id ::tpk/id} (when tehtava
                                  (first (fetch db ::tpk/toimenpidekoodi
                                           #{::tpk/id}
                                           {::tpk/yksiloiva-tunniste tehtava})))
          {toimenpide-id ::tpk/id} (first (fetch db ::tpk/toimenpide
                                            #{::tpk/id}
                                            {::tpk/taso 3
                                             ::tpk/koodi toimenpide}))
          {tehtavaryhma-id ::tr/id} (when tehtavaryhma
                                      (first (fetch db ::tr/tehtavaryhma
                                               #{::tr/id}
                                               {::tr/yksiloiva-tunniste tehtavaryhma})))
          {toimenpideinstanssi-id :id} (first (tpi-q/hae-urakan-toimenpideinstanssi db {:urakka urakka-id :tp toimenpide-id}))
          _ (when (nil? toimenpideinstanssi-id)
              (throw (Exception. "Toimenpideinstanssia ei löydetty")))
          toteumatyyppi (keyword toteumatyyppi)
          ajat (muodosta-ajat ajat)
          kustannusarvioitu-tyo-params (into {}
                                         (map (fn [[k v]]
                                                (if (nil? v)
                                                  [k op/null?]
                                                  [k v]))
                                           {::bs/smallint-v (op/in (into #{} (distinct (map :vuosi ajat))))
                                            ::bs/tehtava tehtava-id
                                            ::bs/tehtavaryhma tehtavaryhma-id
                                            ::bs/tyyppi toteumatyyppi
                                            ::bs/toimenpideinstanssi toimenpideinstanssi-id}))
          tallenna-muutokset-hoitovuosille (keys muutos)
          olemassa-olevat-kustannusarvioidut-tyot-vuosille (fetch db ::bs/kustannusarvioitu-tyo
                                                             #{::bs/id ::bs/smallint-v ::bs/smallint-kk ::bs/summa}
                                                             kustannusarvioitu-tyo-params)
          olemassa-olevat-kustannusarvioidut-tyot (filter (fn [{::bs/keys [smallint-v smallint-kk]}]
                                                            (some #(and (= (:vuosi %) smallint-v)
                                                                     (= (:kuukausi %) smallint-kk))
                                                              ajat))
                                                    olemassa-olevat-kustannusarvioidut-tyot-vuosille)
          uudet-kustannusarvioidut-tyot-ajat (remove (fn [{:keys [vuosi kuukausi]}]
                                                       (some #(and (= vuosi (::bs/smallint-v %))
                                                                (= kuukausi (::bs/smallint-kk %)))
                                                         olemassa-olevat-kustannusarvioidut-tyot-vuosille))
                                               ajat)
          perusosa {:urakka-id urakka-id
                    :tyon-tyyppi :kustannusarvioitu-tyo}]
      (ka-q/merkitse-kustannussuunnitelmat-likaisiksi! db {:toimenpideinstanssi toimenpideinstanssi-id})
      #_(println olemassa-olevat-kustannusarvioidut-tyot)
      #_(println uudet-kustannusarvioidut-tyot-ajat)

      ;; Käsittele päivitettävät kustannusarvioidut tyot
      (when-not (empty? olemassa-olevat-kustannusarvioidut-tyot)
        (doseq [{vuosi ::bs/smallint-v kuukausi ::bs/smallint-kk :as olemassa-oleva-tyo} olemassa-olevat-kustannusarvioidut-tyot]
          ;; TODO: Muutosten tallentaminen ei vielä tee mitään.
          (let [{vanha-summa ::bs/summa id ::bs/id} olemassa-oleva-tyo]
            (tallenna-muutokset-suunnitelmassa db user
              {:vuosi vuosi :kuukausi kuukausi :summa vanha-summa :id id}
              muutos perusosa tallenna-muutokset-hoitovuosille))

          (update! db ::bs/kustannusarvioitu-tyo
            {::bs/osio osio-str
             ::bs/summa summa
             ::bs/summa-indeksikorjattu (when indeksikorjaa?
                                          (indeksikorjaa
                                            (indeksikerroin urakan-indeksit
                                              (pvm/paivamaara->mhu-hoitovuosi-nro
                                                urakan-alkupvm (pvm/luo-pvm-dec-kk vuosi kuukausi 1)))
                                            summa))
             ::bs/muokattu (pvm/nyt)
             ::bs/muokkaaja (:id user)}
            {::bs/id (::bs/id olemassa-oleva-tyo)})))

      ;; Käsittele uudet lisättävät kustannusarvioidut työt
      (when-not (empty? uudet-kustannusarvioidut-tyot-ajat)
        (let [paasopimus (urakat-q/urakan-paasopimus-id db urakka-id)]
          (doseq [{:keys [vuosi kuukausi]} uudet-kustannusarvioidut-tyot-ajat]
            (let [uusi-rivi (insert! db ::bs/kustannusarvioitu-tyo
                              {::bs/osio osio-str
                               ::bs/smallint-v vuosi
                               ::bs/smallint-kk kuukausi
                               ::bs/summa summa
                               ::bs/summa-indeksikorjattu (when indeksikorjaa?
                                                            (indeksikorjaa
                                                              (indeksikerroin urakan-indeksit
                                                                (pvm/paivamaara->mhu-hoitovuosi-nro
                                                                  urakan-alkupvm (pvm/luo-pvm-dec-kk vuosi kuukausi 1)))
                                                              summa))
                               ::bs/tyyppi toteumatyyppi
                               ::bs/tehtava tehtava-id
                               ::bs/tehtavaryhma tehtavaryhma-id
                               ::bs/toimenpideinstanssi toimenpideinstanssi-id
                               ::bs/sopimus paasopimus
                               ::bs/luotu (pvm/nyt)
                               ::bs/luoja (:id user)})]
              ;; TODO: Muutosten tallentaminen ei vielä tee mitään.
              (tallenna-muutokset-suunnitelmassa db user
                {:vuosi vuosi :kuukausi kuukausi :summa summa :id (::bs/id uusi-rivi)}
                muutos perusosa tallenna-muutokset-hoitovuosille)))))
      {:onnistui? true
       :kustannusarvioidut-tyot (hae-urakan-kustannusarvoidut-tyot db user urakka-id)})))


(defn tallenna-kustannusarvioitu-tyo
  [db user {:keys [urakka-id osio tallennettava-asia toimenpide-avain summa ajat muutos]}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu user urakka-id)
  (jdbc/with-db-transaction [db db]
                            (let [toteumatyyppi (mhu/tallennettava-asia->toteumatyyppi tallennettava-asia)
                                  tehtava (mhu/tallennettava-asia->tehtava tallennettava-asia)
                                  tehtava (if (map? tehtava)
                                            (get tehtava toimenpide-avain)
                                            tehtava)
                                  tehtavaryhma (mhu/tallennettava-asia->tehtavaryhma tallennettava-asia)
                                  toimenpide (mhu/toimenpide-avain->toimenpide toimenpide-avain)]
                              (tallenna-kustannusarvioitu-tyo! db user
                                {:toteumatyyppi toteumatyyppi
                                 :osio osio
                                 :tehtava tehtava
                                 :tehtavaryhma tehtavaryhma
                                 :toimenpide toimenpide
                                 :urakka-id urakka-id
                                 :ajat ajat
                                 :summa summa
                                 :indeksikorjaa? (mhu/kustannusarvioitu-tyo-laske-indeksikorjaus? tallennettava-asia)
                                 #_#_:muutos muutos}))))

(defn tallenna-toimenkuva
  [db user {:keys [urakka-id toimenkuva-id toimenkuva]}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu user urakka-id)
  (let [paivitettyjen-rivien-maara (update! db
                                            ::bs/johto-ja-hallintokorvaus-toimenkuva
                                            {::bs/toimenkuva toimenkuva}
                                            {::bs/id toimenkuva-id
                                             ::bs/urakka-id urakka-id})]
    (if (= 0 paivitettyjen-rivien-maara)
      {:virhe "Yhtään riviä ei päivitetty"}
      {:onnistui? true})))

(defrecord Budjettisuunnittelu []
  component/Lifecycle
  (start [this]
    (let [{:keys [db]} this]
      (when (ominaisuus-kaytossa? :mhu-urakka)
        (doto (:http-palvelin this)
          (julkaise-palvelu
            :budjetoidut-tyot (fn [user tiedot]
                                (hae-urakan-budjetoidut-tyot db user tiedot)))

          (julkaise-palvelu
            :budjettitavoite (fn [user tiedot]
                               (hae-urakan-tavoite db user tiedot)))
          (julkaise-palvelu
            :budjettisuunnittelun-indeksit (fn [user tiedot]
                                             (hae-urakan-indeksikertoimet db user tiedot))
            {:kysely-spec ::bs-p/budjettisuunnittelun-indeksit-kysely
             :vastaus-spec ::bs-p/budjettisuunnittelun-indeksit-vastaus})
          (julkaise-palvelu
            :tallenna-budjettitavoite (fn [user tiedot]
                                        (tallenna-urakan-tavoite db user tiedot))
            {:kysely-spec ::bs-p/tallenna-budjettitavoite-kysely
             :vastaus-spec ::bs-p/tallenna-budjettitavoite-vastaus})

          (julkaise-palvelu
            :tallenna-kiinteahintaiset-tyot
            (fn [user tiedot]
              (tallenna-kiinteahintaiset-tyot db user tiedot))
            {:kysely-spec ::bs-p/tallenna-kiinteahintaiset-tyot-kysely
             :vastaus-spec ::bs-p/tallenna-kiinteahintaiset-tyot-vastaus})
          (julkaise-palvelu
            :tallenna-johto-ja-hallintokorvaukset
            (fn [user tiedot]
              (tallenna-johto-ja-hallintokorvaukset db user tiedot))
            {:kysely-spec ::bs-p/tallenna-johto-ja-hallintokorvaukset-kysely
             :vastaus-spec ::bs-p/tallenna-johto-ja-hallintokorvaukset-vastaus})
          (julkaise-palvelu
            :tallenna-kustannusarvioitu-tyo
            (fn [user tiedot]
              (tallenna-kustannusarvioitu-tyo db user tiedot))
            {:kysely-spec ::bs-p/tallenna-kustannusarvioitu-tyo-kysely
             :vastaus-spec ::bs-p/tallenna-kustannusarvioitu-tyo-vastaus})
          (julkaise-palvelu
            :vahvista-kustannussuunnitelman-osa-vuodella
            (fn [user tiedot]
              (vahvista-suunnitelman-osa-hoitovuodelle db user tiedot)))
          (julkaise-palvelu
            :kumoa-suunnitelman-osan-vahvistus-hoitovuodelle
            (fn [user tiedot]
              (kumoa-suunnitelman-osan-vahvistus-hoitovuodelle db user tiedot)))
          (julkaise-palvelu
            :hae-suunnitelman-tilat
            (fn [user tiedot]
              (hae-urakan-suunnitelman-tilat db user tiedot)))
          (julkaise-palvelu
            :tallenna-suunnitelman-osalle-tila
            (fn [user tiedot]
              (tallenna-suunnitelman-osalle-tila db user tiedot)))
          (julkaise-palvelu
            :tallenna-suunnitelman-muutos
            (fn [user tiedot]
              (tallenna-suunnitelman-muutos db user tiedot)))
          (julkaise-palvelu
            :tallenna-toimenkuva
            (fn [user tiedot]
              (tallenna-toimenkuva db user tiedot))
            {:kysely-spec ::bs-p/tallenna-toimenkuva-kysely
             :vastaus-spec ::bs-p/tallenna-toimenkuva-vastaus}))))
    this)

  (stop [this]
    (poista-palvelut (:http-palvelin this)
                     :budjetoidut-tyot
                     :budjettitavoite
                     :budjettisuunnittelun-indeksit
                     :hae-suunnitelman-tilat
                     :vahvista-kustannussuunnitelman-osa-vuodella
                     :kumoa-suunnitelman-osan-vahvistus-hoitovuodelle
                     :tallenna-suunnitelman-osalle-tila
                     :tallenna-suunnitelman-muutos
                     :tallenna-budjettitavoite
                     :tallenna-kiinteahintaiset-tyot
                     :tallenna-johto-ja-hallintokorvaukset
                     :tallenna-kustannusarvioitu-tyo
                     :tallenna-toimenkuva)
    this))
