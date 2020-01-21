(ns harja.ui.taulukko.impl.alue
  (:require [harja.ui.taulukko.impl.grid :as g]
            [harja.ui.taulukko.protokollat.grid :as p]
            [harja.ui.taulukko.protokollat.grid-osa :as gop]
            [harja.loki :refer [log]]))

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

(declare ->Taulukko ->Rivi)

(defrecord Rivi [id alueet koko osat parametrit]
  p/IGrid
  (-osat [this]
    (g/grid-osat this))
  (-osat [this polku]
    (g/grid-osat this polku))
  (-aseta-osat! [this osat]
    (g/aseta-osat! this osat))
  (-aseta-osat! [this polku osat]
    (g/aseta-osat! this polku osat))
  (-paivita-osat! [this f]
    (g/paivita-osat! this f))
  (-paivita-osat! [this polku f]
    (g/paivita-osat! this polku f))

  (-lisaa-rivi! [this solu]
    (p/lisaa-rivi! this solu (count (p/lapset this))))
  (-lisaa-rivi! [this solu index]
    (g/lisaa-rivi! this solu index))
  (-lisaa-sarake! [this solu]
    (p/lisaa-sarake! this solu (count (p/lapset this))))
  (-lisaa-sarake! [this solu index]
    (g/lisaa-sarake! this solu index))

  (-koko [this]
    (g/grid-koko this))
  (-koot [this]
    (g/grid-koot this))
  (-aseta-koko! [this koko]
    (g/aseta-koko-grid this koko))
  (-paivita-koko! [this f]
    (g/paivita-koko-grid this f))
  (-alueet [this]
    (g/grid-alueet this))
  (-aseta-alueet! [this alueet]
    ;;TODO Rajoita alueiden asettamista
    (g/aseta-alueet! this alueet))
  (-paivita-alueet! [this f]
    ;;TODO Rajoita alueiden asettamista
    (g/paivita-alueet! this f))
  (-aseta-root-fn [this f]
    (g/aseta-root-fn this f))

  (-parametrit [this]
    (g/grid-parametrit this))
  (-aseta-parametrit! [this parametrit]
    (g/aseta-parametrit! this parametrit))
  (-paivita-parametrit! [this f]
    (g/paivita-parametrit! this f))

  (-rivi [this tunniste] (log "KUTSUTTIIN -rivi FUNKTIOTA Grid:ille"))
  (-sarake [this tunniste] (log "KUTSUTTIIN -sarake FUNKTIOTA Grid:ille"))
  (-solu [this tunniste] (log "KUTSUTTIIN -solu FUNKTIOTA Grid:ille"))
  gop/IGridOsa
  (-id [this]
    (:id this))
  (-id? [this id]
    (= (:id this) id))
  (-nimi [this]
    (::g/nimi this))
  (-aseta-nimi [this nimi]
    (assoc this ::g/nimi nimi))
  gop/IPiirrettava
  (-piirra [this]
    [:<>
     [g/piirra-grid this]])
  gop/IPiillota
  (-piillota! [this]
    (g/piillota! this))
  (-nayta! [this]
    (g/nayta! this))
  (-piillotettu? [this]
    (g/piillotettu? this))
  gop/IKopioi
  (-kopioi [this]
    (g/grid-kopioi this ->Rivi)))

(defrecord Taulukko [id alueet koko osat parametrit]
  p/IGrid
  (-osat [this]
    (g/grid-osat this))
  (-osat [this polku]
    (g/grid-osat this polku))
  (-aseta-osat! [this osat]
    ;;TODO Rajoita solujen asettamista
    (g/aseta-osat! this osat))
  (-aseta-osat! [this polku osat]
    ;;TODO Rajoita solujen asettamista
    (g/aseta-osat! this polku osat))
  (-paivita-osat! [this f]
    ;;TODO Rajoita solujen asettamista
    (g/paivita-osat! this f))
  (-paivita-osat! [this polku f]
    ;;TODO Rajoita solujen asettamista
    (g/paivita-osat! this polku f))

  (-lisaa-rivi! [this solu]
    (p/lisaa-rivi! this solu (count (p/lapset this))))
  (-lisaa-rivi! [this solu index]
    (g/lisaa-rivi! this solu index))
  (-lisaa-sarake! [this solu]
    (p/lisaa-sarake! this solu (count (p/lapset this))))
  (-lisaa-sarake! [this solu index]
    (g/lisaa-sarake! this solu index))

  (-koko [this]
    (g/grid-koko this))
  (-koot [this]
    (g/grid-koot this))
  (-aseta-koko! [this koko]
    (g/aseta-koko-grid this koko))
  (-paivita-koko! [this f]
    (g/paivita-koko-grid this f))
  (-alueet [this]
    (g/grid-alueet this))
  (-aseta-alueet! [this alueet]
    (g/aseta-alueet! this alueet))
  (-paivita-alueet! [this f]
    (g/paivita-alueet! this f))
  (-aseta-root-fn [this f]
    (g/aseta-root-fn this f))

  (-parametrit [this]
    (g/grid-parametrit this))
  (-aseta-parametrit! [this parametrit]
    (g/aseta-parametrit! this parametrit))
  (-paivita-parametrit! [this f]
    (g/paivita-parametrit! this f))

  (-rivi [this tunniste] (log "KUTSUTTIIN -rivi FUNKTIOTA Grid:ille"))
  (-sarake [this tunniste] (log "KUTSUTTIIN -sarake FUNKTIOTA Grid:ille"))
  (-solu [this tunniste] (log "KUTSUTTIIN -solu FUNKTIOTA Grid:ille"))
  gop/IGridOsa
  (-id [this]
    (:id this))
  (-id? [this id]
    (= (:id this) id))
  (-nimi [this]
    (::g/nimi this))
  (-aseta-nimi [this nimi]
    (assoc this ::g/nimi nimi))
  gop/IPiirrettava
  (-piirra [this]
    [:<>
     [g/piirra-grid this]])
  gop/IPiillota
  (-piillota! [this]
    (g/piillota! this))
  (-nayta! [this]
    (g/nayta! this))
  (-piillotettu? [this]
    (g/piillotettu? this))
  gop/IKopioi
  (-kopioi [this]
    (g/grid-kopioi this ->Taulukko)))
