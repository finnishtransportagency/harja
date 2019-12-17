(ns harja.ui.taulukko.alue
  (:require [harja.ui.taulukko.grid :as g]
            [harja.loki :refer [log]]
            [reagent.core :as r]
            [cljs.spec.alpha :as s]
            [harja.ui.taulukko.grid-protokollat :as p]))

(defn alue-nollakohtaan [alue]
  (let [nollakohtaan (fn [[alku loppu]]
                       (let [vali (- loppu alku)]
                         [0 vali]))]
    (-> alue
        (update :sarakkeet nollakohtaan)
        (update :rivit nollakohtaan))))

(defn samat-osat? [osat]
  (let [sama-tyyppi? (apply = (map type osat))
        grid-lapsia? (instance? g/Grid (first osat))
        samanlaiset-gridit? (when (and sama-tyyppi? grid-lapsia?)
                              (apply = (map (fn [grid]
                                              (map (fn [alue]
                                                     (alue-nollakohtaan alue))
                                                   (p/alueet grid)))
                                            osat)))
        samanlaiset-osat? (and sama-tyyppi?
                               (or (not grid-lapsia?)
                                   samanlaiset-gridit?))]
    (if grid-lapsia?
      (apply = (map samat-osat? osat))
      samanlaiset-osat?)))

(defrecord Rivi [id]
  p/IGrid
  (-solut [this]
    (::g/osat this))
  (-aseta-solut [this solut]
    (assoc this ::g/osat solut))
  (-paivita-solut [this f]
    (update this ::g/osat f))
  (-koko [this]
    (g/grid-koko this))
  (-koot [this]
    (g/grid-koot this))
  (-aseta-koko! [this koko]
    (g/aseta-koko-grid this koko))
  (-paivita-koko! [this f]
    (g/paivita-koko-grid this f))
  (-alueet [this]
    (::g/alueet this))
  (-aseta-alueet [this alueet]
    ;;TODO Rajoita alueiden asettamista
    (assoc this ::g/alueet alueet))
  (-paivita-alueet [this f]
    ;;TODO Rajoita alueiden asettamista
    (update this ::g/alueet f))

  (-rivi [this tunniste] (log "KUTSUTTIIN -rivi FUNKTIOTA Grid:ille"))
  (-sarake [this tunniste] (log "KUTSUTTIIN -sarake FUNKTIOTA Grid:ille"))
  (-solu [this tunniste] (log "KUTSUTTIIN -solu FUNKTIOTA Grid:ille"))
  p/IGridOsa
  (-id [this]
    (:id this))
  (-id? [this id]
    (= (:id this) id))
  (-nimi [this]
    (::g/nimi this))
  (-aseta-nimi [this nimi]
    (assoc this ::g/nimi nimi))
  p/IPiirrettava
  (-piirra [this]
    [:<>
     [g/piirra-grid this]]))

(defrecord Taulukko [id]
  p/IGrid
  (-solut [this]
    (::g/osat this))
  (-aseta-solut [this solut]
    ;;TODO Rajoita solujen asettamista
    (assoc this ::g/osat solut))
  (-paivita-solut [this f]
    ;;TODO Rajoita solujen asettamista
    (update this ::g/osat f))
  (-koko [this]
    (g/grid-koko this))
  (-koot [this]
    (g/grid-koot this))
  (-aseta-koko! [this koko]
    (g/aseta-koko-grid this koko))
  (-paivita-koko! [this f]
    (g/paivita-koko-grid this f))
  (-alueet [this]
    (::g/alueet this))
  (-aseta-alueet [this alueet]
    (assoc this ::g/alueet alueet))
  (-paivita-alueet [this f]
    (update this ::g/alueet f))

  (-rivi [this tunniste] (log "KUTSUTTIIN -rivi FUNKTIOTA Grid:ille"))
  (-sarake [this tunniste] (log "KUTSUTTIIN -sarake FUNKTIOTA Grid:ille"))
  (-solu [this tunniste] (log "KUTSUTTIIN -solu FUNKTIOTA Grid:ille"))
  p/IGridOsa
  (-id [this]
    (:id this))
  (-id? [this id]
    (= (:id this) id))
  (-nimi [this]
    (::g/nimi this))
  (-aseta-nimi [this nimi]
    (assoc this ::g/nimi nimi))
  p/IPiirrettava
  (-piirra [this]
    [:<>
     [g/piirra-grid this]]))

(defn rivi
  "Rivi on grid, mutta varmistetaan, että alueessa on vain yksi rivi."
  [{:keys [nimi koko osat rajapinnan-polku] :as asetukset} alueet]
  {:pre [(g/validi-grid-asetukset? (assoc asetukset :alueet alueet))
         (= (count alueet) 1)
         #_(= -1 (apply - (:rivit (first alueet))))]
   :post [(instance? Rivi %)
          (symbol? (p/id %))]}
  (let [id (gensym "rivi")
        koko (r/atom {id koko})
        rivi (cond-> (->Rivi id)
                     nimi (assoc ::g/nimi nimi)
                     alueet (assoc ::g/alueet alueet)
                     osat (assoc ::g/osat osat)
                     rajapinnan-polku (assoc ::g/rajapinnan-polku rajapinnan-polku))
        rivi (g/paivita-kaikki-lapset (assoc rivi ::g/koko koko)
                                      (fn [& _] true)
                                      (fn [lapsi]
                                        (let [koot (when (satisfies? p/IGrid lapsi)
                                                     (p/koot lapsi))
                                              _ (when koot
                                                  (swap! koko (fn [koko]
                                                                (merge koko koot))))
                                              lapsi (assoc lapsi ::g/koko koko)]
                                          lapsi)))]
    rivi))

(defn taulukko
  "Taulukko on grid, mutta varmistetaan, että kaikki osat ovat samanlaisia"
  [{:keys [nimi alueet koko rajapinnan-polku] :as asetukset} osat]
  {:pre [(g/validi-grid-asetukset? (assoc asetukset :osat osat))
         (samat-osat? osat)]
   :post [(instance? Taulukko %)
          (symbol? (p/id %))]}
  (let [id (gensym "rivi")
        koko (r/atom {id koko})
        taulukko (cond-> (->Taulukko id)
                         nimi (assoc ::g/nimi nimi)
                         alueet (assoc ::g/alueet alueet)
                         osat (assoc ::g/osat osat)
                         rajapinnan-polku (assoc ::g/rajapinnan-polku rajapinnan-polku))
        taulukko (g/paivita-kaikki-lapset (assoc taulukko ::g/koko koko)
                                          (fn [& _] true)
                                          (fn [lapsi]
                                            (let [koot (when (satisfies? p/IGrid lapsi)
                                                         (p/koot lapsi))
                                                  _ (when koot
                                                      (swap! koko (fn [koko]
                                                                    (merge koko koot))))
                                                  lapsi (assoc lapsi ::g/koko koko)]
                                              lapsi)))]
    taulukko))
