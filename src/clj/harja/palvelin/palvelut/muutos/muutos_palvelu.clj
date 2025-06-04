(ns harja.palvelin.palvelut.muutos.muutos-palvelu
  (:require [clojure.java.jdbc :as jdbc]
            [com.stuartsierra.component :as component]
            [harja.pvm :as pvm]
            [harja.palvelin.asetukset :refer [ominaisuus-kaytossa?]]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.domain.oikeudet :as oikeudet]
            [harja.kyselyt.budjettisuunnittelu :as budjettisuunnittelu-q]
            [harja.kyselyt [muutos-kyselyt :as muutos-kyselyt]]
            [harja.kyselyt.konversio :as konv]
            [taoensso.timbre :as log]))


(defn tavoitehinnan-muutos [muutokset]
  (mapv (fn [rivi]
          (let [total (->> (:kustannusvaikutukset rivi)
                        (map :summa)
                        (reduce + 0))]
            (assoc rivi :tavoitehinnan-muutos total)))
    muutokset))

(defn hae-urakan-muutostiedot
  [db user {:keys [urakka-id valittu-hoitokausi] :as tiedot}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu user urakka-id)
  (log/debug "hae-urakan-muutostiedot: " tiedot)
  (let [hoitokauden-alkuvuosi (pvm/vuosi (first valittu-hoitokausi))
        vastaus (mapv
                  (fn [rivi]
                    (-> rivi
                      (update :kustannusvaikutukset #(konv/jsonb->clojuremap %))
                      (update :tehtavat_ja_maarat #(konv/jsonb->clojuremap %))
                      (update :liitteet #(konv/jsonb->clojuremap %))))
                  (muutos-kyselyt/hae-urakan-hoitovuoden-muutostiedot db {:urakka urakka-id
                                                                          :hoitokauden_alkuvuosi hoitokauden-alkuvuosi}))
        kirjatut-muutokset (tavoitehinnan-muutos vastaus)
        budjettitavoiteet (budjettisuunnittelu-q/budjettitavoite-vuodelle db urakka-id hoitokauden-alkuvuosi)
        muutosten-vaikutus-yhteensa (reduce + 0 (map :tavoitehinnan-muutos kirjatut-muutokset))]
    (prn "Haetut muutostiedot: " kirjatut-muutokset)
    (prn "Haetut budjettitavoitteet : " budjettitavoiteet)
    ;; kirjatut muutokset jos hoitokausi 2025-2026 tai jälkeen
    {:kirjatut-muutokset kirjatut-muutokset
     ;; TODO: laskennat lasketuille muutoksille jos hoitokausi 2025-2026 tai jälkeen
     :lasketut-muutokset []
     ;; TODO: laskennat rahavarausten muutoksille jos hoitokausi 2025-2026 tai jälkeen
     :rahavarausten-muutokset []
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


(defn tallenna-muutos [db user {:keys [urakka-id valittu-hoitokausi muutos] :as tiedot}]
  (log/debug "tallenna-muutos: " tiedot)
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu user urakka-id)
  (let [kustannusvaikutukset (:kustannusvaikutukset muutos)
        tehtava-ja-maaramuutokset (:tehtavat_ja_maarat muutos)
        liitteet (:liitteet muutos)]
    (jdbc/with-db-transaction [db db]
      ;; TODO: muutoksen tallennus tähän
      )))


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

    this)

  (stop [this]
    (poista-palvelut (:http-palvelin this)
      :hae-urakan-muutostiedot
      :tallenna-muutos)
    this))
