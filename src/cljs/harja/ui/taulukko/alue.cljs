(ns harja.ui.taulukko.alue
  (:require [harja.ui.taulukko.grid :as g]
            [cljs.spec.alpha :as s]
            [harja.ui.taulukko.protokollat :as p]))

(defn alue-nollakohtaan [alue]
  (let [nollakohtaan (fn [[alku loppu]]
                       (let [vali (- loppu alku)]
                         [0 vali]))]
    (-> alue
        (update :sarakkeet nollakohtaan)
        (update :rivit nollakohtaan))))

(defn samat-osat? [osat]
  (let [sama-tyyppi? (apply = (map type osat))
        grid? (instance? g/Grid (first osat))
        samanlaiset-gridit? (when (and sama-tyyppi? grid?)
                              (apply = (map (fn [grid]
                                              (map (fn [alue]
                                                     (alue-nollakohtaan alue))
                                                   (p/alueet grid)))
                                            osat)))]
    (if (not grid?)
      sama-tyyppi?
      (recur ))
    (and sama-tyyppi?
         (or (not grid?)
             samanlaiset-gridit?))))

(defrecord Rivi [id]
  p/IGrid
  (-solut [this]
    (::solut this))
  (-aseta-solut [this solut]
    (assoc this ::solut solut))
  (-paivita-solut [this f]
    (update this ::solut f))
  (-koko [this]
    (-> this ::koko deref (get (p/nimi this))))
  (-aseta-koko! [this koko]
    (g/aseta-koko-grid this koko))
  (-paivita-koko! [this f]
    (g/paivita-koko-grid this f))
  (-alueet [this]
    (::alueet this))
  (-aseta-alueet [this alueet]
    ;;TODO Rajoita alueiden asettamista
    (assoc this ::alueet alueet))
  (-paivita-alueet [this f]
    ;;TODO Rajoita alueiden asettamista
    (update this ::alueet f))

  (-rivi [this tunniste] (log "KUTSUTTIIN -rivi FUNKTIOTA Grid:ille"))
  (-sarake [this tunniste] (log "KUTSUTTIIN -sarake FUNKTIOTA Grid:ille"))
  (-solu [this tunniste] (log "KUTSUTTIIN -solu FUNKTIOTA Grid:ille"))
  p/IGridOsa
  (-id [this]
    (:id this))
  (-id? [this id]
    (= (:id this) id))
  (-nimi [this]
    (::nimi this))
  (-aseta-nimi [this nimi]
    (assoc this ::nimi nimi))
  p/IPiirrettava
  (-piirra [this]
    [g/piirra-grid this]))

(defrecord Taulukko [id]
  p/IGrid
  (-solut [this]
    (::solut this))
  (-aseta-solut [this solut]
    ;;TODO Rajoita solujen asettamista
    (assoc this ::solut solut))
  (-paivita-solut [this f]
    ;;TODO Rajoita solujen asettamista
    (update this ::solut f))
  (-koko [this]
    (-> this ::koko deref (get (p/nimi this))))
  (-aseta-koko! [this koko]
    (g/aseta-koko-grid this koko))
  (-paivita-koko! [this f]
    (g/paivita-koko-grid this f))
  (-alueet [this]
    (::alueet this))
  (-aseta-alueet [this alueet]
    (assoc this ::alueet alueet))
  (-paivita-alueet [this f]
    (update this ::alueet f))

  (-rivi [this tunniste] (log "KUTSUTTIIN -rivi FUNKTIOTA Grid:ille"))
  (-sarake [this tunniste] (log "KUTSUTTIIN -sarake FUNKTIOTA Grid:ille"))
  (-solu [this tunniste] (log "KUTSUTTIIN -solu FUNKTIOTA Grid:ille"))
  p/IGridOsa
  (-id [this]
    (:id this))
  (-id? [this id]
    (= (:id this) id))
  (-nimi [this]
    (::nimi this))
  (-aseta-nimi [this nimi]
    (assoc this ::nimi nimi))
  p/IPiirrettava
  (-piirra [this]
    [g/piirra-grid this]))

(defn rivi
  "Rivi on grid, mutta varmistetaan, että alueessa on vain yksi rivi."
  [{:keys [nimi koko jarjestys osat] :as asetukset} alueet]
  {:pre [(g/validi-grid-asetukset? (assoc asetukset :alueet alueet))
         (= (count alueet) 1)
         (apply = (:rivit (first alueet)))]
   :post [(instance? Rivi %)
          (symbol? (p/id %))]}
  (let [id (gensym "rivi")]
    (cond-> (->Rivi id)
            nimi (assoc ::nimi nimi)
            alueet (assoc ::alueet alueet)
            koko (assoc ::koko koko)
            osat (assoc ::osat osat))))

(defn taulukko
  "Taulukko on grid, mutta varmistetaan, että kaikki osat ovat samanlaisia"
  [{:keys [nimi alueet koko jarjestys] :as asetukset} osat]
  {:pre [(g/validi-grid-asetukset? (assoc asetukset :osat osat))
         (samat-osat? osat)]
   :post [(instance? Taulukko %)
          (symbol? (p/id %))]}
  (let [id (gensym "rivi")]
    (cond-> (->Taulukko id)
            nimi (assoc ::nimi nimi)
            alueet (assoc ::alueet alueet)
            koko (assoc ::koko koko)
            osat (assoc ::osat osat))))
