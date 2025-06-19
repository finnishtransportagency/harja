(ns harja.palvelin.palvelut.muutos.muutos-palvelu
  (:require [clojure.java.jdbc :as jdbc]
            [com.stuartsierra.component :as component]
            [harja.domain.kulut :as kulut-domain]
            [harja.kyselyt.kulut :as kulu-kyselyt]
            [harja.kyselyt.urakat :as q-urakat]
            [harja.pvm :as pvm]
            [harja.palvelin.asetukset :refer [ominaisuus-kaytossa?]]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.domain.oikeudet :as oikeudet]
            [harja.kyselyt.budjettisuunnittelu :as budjettisuunnittelu-q]
            [harja.kyselyt [muutos-kyselyt :as muutos-kyselyt]]
            [harja.kyselyt.rahavaraukset :as rahavaraus-kyselyt]
            [harja.kyselyt.konversio :as konv]
            [harja.tyokalut.yleiset :as yleiset]
            [taoensso.timbre :as log]))


(defn tavoitehinnan-muutos [muutokset]
  (mapv (fn [rivi]
          (let [total (->> (:kustannusvaikutukset rivi)
                        (map :summa)
                        (reduce + 0))]
            (assoc rivi :tavoitehinnan-muutos total)))
    muutokset))

(defn- rahavarausten-summarivi [rahavaraukset]
  (let [{:keys [summa-indeksikorjattu toteumat]}
        (reduce (fn [acc {:keys [summa-indeksikorjattu toteumat]}]
                  {:summa-indeksikorjattu (+ (:summa-indeksikorjattu acc 0)
                                            (or summa-indeksikorjattu 0))
                   :toteumat (+ (:toteumat acc 0)
                               (or toteumat 0))})
          {}
          rahavaraukset)]
    {:id :yhteenveto
     :summa-indeksikorjattu summa-indeksikorjattu
     :toteumat toteumat
     :tavoitehinnan-muutos (- toteumat summa-indeksikorjattu)}))


(defn hae-urakan-muutostiedot
  [db user {:keys [urakka-id valittu-hoitokausi] :as tiedot}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu user urakka-id)
  (log/debug "hae-urakan-muutostiedot: " tiedot)
  (let [hoitokauden-alkuvuosi (pvm/vuosi (first valittu-hoitokausi))
        kirjatut-muutokset-vastaus (mapv
                                     (fn [rivi]
                                       (-> rivi
                                         (update :kustannusvaikutukset #(konv/jsonb->clojuremap %))
                                         (update :tehtavat_ja_maarat #(konv/jsonb->clojuremap %))
                                         (update :liitteet #(konv/jsonb->clojuremap %))))
                                     (muutos-kyselyt/hae-urakan-hoitovuoden-muutostiedot db {:urakka urakka-id
                                                                                             :hoitokauden_alkuvuosi hoitokauden-alkuvuosi}))
        kirjatut-muutokset (tavoitehinnan-muutos kirjatut-muutokset-vastaus)
        rahavarausten-suunnitelmat (map
                                     #(select-keys % [:id :nimi :summa-indeksikorjattu])
                                     (rahavaraus-kyselyt/hae-urakan-suunnitellut-rahavarausten-kustannukset db {:urakka_id urakka-id
                                                                                                               ;; haetaan vain valitulle hoitovuodelle
                                                                                                               :alkuvuosi hoitokauden-alkuvuosi
                                                                                                               :loppuvuosi (inc hoitokauden-alkuvuosi)}))
        rahavarausten-toteumat (muutos-kyselyt/rahavarausten-toteumat db {:urakka urakka-id
                                                                          :hoitokauden_alkuvuosi hoitokauden-alkuvuosi})
        rahavaraukset (yleiset/yhdista-mapit-avaimella rahavarausten-suunnitelmat rahavarausten-toteumat :id)
        rahavarausmuutosten-syyt (muutos-kyselyt/rahavarausmuutosten-syyt db {:urakka urakka-id
                                                                              :hoitokauden_alkuvuosi hoitokauden-alkuvuosi})
        rahavaraukset (yleiset/yhdista-mapit-avaimella rahavaraukset rahavarausmuutosten-syyt :id)
        rahavaraukset (mapv
                        ;; lasketaan erotus vain jos molemmat arvot ovat olemassa
                        #(if (and (:summa-indeksikorjattu %)
                               (:toteumat %))
                           (assoc % :tavoitehinnan-muutos (- (:toteumat %)
                                                            (:summa-indeksikorjattu %)))
                           %)
                        rahavaraukset)
        rahavaraukset-yhteensa (rahavarausten-summarivi rahavaraukset)
        rahavaraukset (conj rahavaraukset rahavaraukset-yhteensa)
        budjettitavoiteet (budjettisuunnittelu-q/budjettitavoite-vuodelle db urakka-id hoitokauden-alkuvuosi)
        muutosten-vaikutus-yhteensa (reduce + 0
                                      (concat
                                        (map :tavoitehinnan-muutos kirjatut-muutokset)
                                        [(:tavoitehinnan-muutos (last rahavaraukset))]))]
    ;; kirjatut muutokset jos hoitokausi 2025-2026 tai jälkeen
    {:kirjatut-muutokset kirjatut-muutokset
     ;; TODO: laskennat lasketuille muutoksille jos hoitokausi 2025-2026 tai jälkeen
     :lasketut-muutokset []
     :rahavarausten-muutokset rahavaraukset
     ;; TODO: laskennat vanhojen tavoitehintojen muutoksille jos hoitokausi ennen 2025-2026
     :tavoitehinnan-muutokset []
     ;; TODO: laskennat vanhojen suunniteltujen määrien muutoksille jos hoitokausi ennen 2025-2026
     :suunniteltujen-maarien-muutokset []
     :budjettitavoitteet {:indeksikorjaus-vahvistettu? (:indeksikorjaus-vahvistettu budjettitavoiteet)
                          :hoitovuoden-alun-indeksikorjattu-tavoitehinta (:tavoitehinta-indeksikorjattu budjettitavoiteet)
                          :muutosten-vaikutus-yhteensa muutosten-vaikutus-yhteensa
                          :hoitovuoden-lopun-tavoitehinta (when (:tavoitehinta-indeksikorjattu budjettitavoiteet)
                                                            (+
                                                              (:tavoitehinta-indeksikorjattu budjettitavoiteet)
                                                              ;; TODO: tässä huomioitava kaikkien muutosten vaikutus, työversiossa vasta kirjatut muutokset mukana
                                                              muutosten-vaikutus-yhteensa))}}))


(defn- tallenna-johto-ja-hallintokorvauksen-muutokset
  "Tallentaa johto- ja hallintokorvauksen muutokset tietokantaan kuluiksi, linkittää muutokseen."
  [db user urakka muutos-id-ja-versio rivit]
  (doseq [rivi rivit
          :let [kulu {:urakka (:id urakka)
                      :erapaiva (:pvm rivi)
                      :numero (:numero rivi)
                      :kayttaja (:id user)
                      :lisatieto "Muutoksesta automaattisesti luotu kulu"
                      :laskun_numero (:laskun-numero rivi)
                      :koontilaskun-kuukausi (kulut-domain/pvm->koontilaskun-kuukausi (:pvm rivi) (:alkupvm urakka))
                      :kokonaissumma (:tavoitehinnan-muutos rivi)}
                ;; jotta saadaan talteen muutoshistoria, aina luodaan uusi kulu ja sen kohdistus, vanhat merkitään poistetuksi
                kulu-id-db (:id (kulu-kyselyt/luo-kulu<! db kulu))]
          ]
    ;; TODO: luo kulun kohdistus tätän!
    (muutos-kyselyt/luo-muutos-kulu-linkitys<! db {:versio (:versio muutos-id-ja-versio)
                                                   :muutos (:id muutos-id-ja-versio)
                                                   :kulu kulu-id-db})))

(defn tallenna-muutos [db user {:keys [urakka-id valittu-hoitokausi muutos] :as tiedot}]
  (log/debug "tallenna-muutos: " tiedot)
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu user urakka-id)
  (let [urakka (first (q-urakat/hae-urakka db urakka-id))
        kustannusvaikutukset (:kustannusvaikutukset muutos)
        tehtava-ja-maaramuutokset (:tehtavat_ja_maarat muutos)
        johto-ja-hallintokorvausmuutokset (:johto-ja-hallintokorvausmuutokset muutos)
        liitteet (:liitteet muutos)
        muutos {:id (:id muutos)
                :versio (:versio muutos)
                :urakka urakka-id
                :voimassa_alkaen (:voimassa_alkaen muutos)
                :nimi (:nimi muutos)
                :syy (:syy muutos)
                :kulu_kohdistus (:kulu-kohdistus muutos)
                :luonnos (:luonnos muutos)
                :hoitokauden_alkuvuosi (pvm/vuosi (first valittu-hoitokausi))
                :tyyppi (:tyyppi muutos)
                :kayttaja (:id user)}]
    (jdbc/with-db-transaction [db db]
      (let [muutos-paluurivi (if (:id muutos)
                               (muutos-kyselyt/paivita-muutos!
                                 db
                                 muutos)
                               (muutos-kyselyt/luo-muutos<!
                                 db
                                 muutos))]
      (case (:tyyppi muutos)
        "johto-ja-hallintokorvaus")
      (tallenna-johto-ja-hallintokorvauksen-muutokset db user urakka muutos-paluurivi johto-ja-hallintokorvausmuutokset))
    (hae-urakan-muutostiedot db user {:urakka-id urakka-id
                                      :valittu-hoitokausi valittu-hoitokausi}))))

(defn tallenna-rahavarausmuutosten-syyt
  [db user {:keys [urakka-id valittu-hoitokausi rivit]}]
  (log/debug "Tallenna rahavarausmuutosten syyt" urakka-id valittu-hoitokausi rivit)
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu user urakka-id)
  (let [hoitokauden-alkuvuosi (pvm/vuosi (first valittu-hoitokausi))]
    (jdbc/with-db-transaction [db db]
      (doseq [{:keys [id syy]} rivit]
        (muutos-kyselyt/upsert-rahavarausmuutosten-syyt!
          db
          {:urakka urakka-id
           :hoitokauden_alkuvuosi hoitokauden-alkuvuosi
           :rahavaraus_id id
           :syy syy
           :kayttaja (:id user)}))
      (hae-urakan-muutostiedot db user {:urakka-id urakka-id
                                        :valittu-hoitokausi valittu-hoitokausi}))))

(defrecord Muutos [asetukset]
  component/Lifecycle
  (start [this]

    (when (ominaisuus-kaytossa? :mhu-muutokset)
      (julkaise-palvelu (:http-palvelin this)
        :hae-urakan-muutostiedot
        (fn [user tiedot]
          (hae-urakan-muutostiedot
            (:db this)
            user
            tiedot))))

    (julkaise-palvelu (:http-palvelin this)
      :tallenna-muutos
      (fn [user tiedot]
        (tallenna-muutos (:db this) user tiedot)))

    (julkaise-palvelu (:http-palvelin this)
      :tallenna-rahavarausmuutosten-syyt
      (fn [user tiedot]
        (tallenna-rahavarausmuutosten-syyt (:db this) user tiedot)))

    this)

  (stop [this]
    (poista-palvelut (:http-palvelin this)
      :hae-urakan-muutostiedot
      :tallenna-muutos
      :tallenna-rahavarausmuutosten-syyt)
    this))
