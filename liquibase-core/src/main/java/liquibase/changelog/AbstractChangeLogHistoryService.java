package liquibase.changelog;

import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Scope;
import liquibase.changelog.filter.ContextChangeSetFilter;
import liquibase.changelog.filter.DbmsChangeSetFilter;
import liquibase.database.Database;
import liquibase.exception.DatabaseException;
import liquibase.exception.DatabaseHistoryException;

import java.util.Date;
import java.util.List;

public abstract class AbstractChangeLogHistoryService implements ChangeLogHistoryService {

    private Database database;
    private String deploymentId;

    public Database getDatabase() {
        return database;
    }

    @Override
    public void setDatabase(Database database) {
        this.database = database;
    }

    @Override
    public void reset() {

    }

    public ChangeSet.RunStatus getRunStatus(final ChangeSet changeSet)
        throws DatabaseException, DatabaseHistoryException {
        RanChangeSet foundRan = getRanChangeSet(changeSet);

        if (foundRan == null) {
            return ChangeSet.RunStatus.NOT_RAN;
        } else {
            if (foundRan.getLastCheckSum() == null) {
                try {
                    Scope.getCurrentScope().getLog(getClass()).info("Updating NULL md5sum for " + changeSet.toString());
                    replaceChecksum(changeSet);
                } catch (DatabaseException e) {
                    throw new DatabaseException(e);
                }

                return ChangeSet.RunStatus.ALREADY_RAN;
            } else {
                if (foundRan.getLastCheckSum().equals(changeSet.generateCheckSum())) {
                    return ChangeSet.RunStatus.ALREADY_RAN;
                } else {
                    if (changeSet.shouldRunOnChange()) {
                        return ChangeSet.RunStatus.RUN_AGAIN;
                    } else {
                        return ChangeSet.RunStatus.INVALID_MD5SUM;
                    }
                }
            }
        }
    }

    public void upgradeChecksums(final DatabaseChangeLog databaseChangeLog, final Contexts contexts,
                                 LabelExpression labels) throws DatabaseException {
        for (RanChangeSet ranChangeSet : this.getRanChangeSets()) {
            if (ranChangeSet.getLastCheckSum() == null) {
                ChangeSet changeSet = databaseChangeLog.getChangeSet(ranChangeSet);
                if ((changeSet != null) && new ContextChangeSetFilter(contexts).accepts(changeSet).isAccepted() &&
                    new DbmsChangeSetFilter(getDatabase()).accepts(changeSet).isAccepted()
                    ) {
                    Scope.getCurrentScope().getLog(getClass()).fine(
                            "Updating null or out of date checksum on changeSet " + changeSet + " to correct value"
                    );
                    replaceChecksum(changeSet);
                }
            }
        }
    }

    @Override
    public RanChangeSet getRanChangeSet(final ChangeSet changeSet) throws DatabaseException, DatabaseHistoryException {
        for (RanChangeSet ranChange : getRanChangeSets()) {
            if (ranChange.isSameAs(changeSet)) {
                return ranChange;
            }
        }
        return null;
    }

    @Override
    public Date getRanDate(ChangeSet changeSet) throws DatabaseException, DatabaseHistoryException {
        RanChangeSet ranChange = getRanChangeSet(changeSet);
        if (ranChange == null) {
            return null;
        } else {
            return ranChange.getDateExecuted();
        }
    }

    /**
     *
     * Return the last deployment ID from the changesets that have been run or null
     *
     * @return   String
     * @throws   DatabaseException
     *
     */
     public String getLastDeploymentId() throws DatabaseException {
         List<RanChangeSet> ranChangeSetsList = getRanChangeSets();
         if (ranChangeSetsList == null || ranChangeSetsList.size() == 0) {
             return null;
         }
         RanChangeSet lastRanChangeSet = ranChangeSetsList.get(ranChangeSetsList.size() - 1);
         return lastRanChangeSet.getDeploymentId();
    }

    protected abstract void replaceChecksum(ChangeSet changeSet) throws DatabaseException;

    public String getDeploymentId() {
        return this.deploymentId;
    }

    public void resetDeploymentId() {
        this.deploymentId = null;
    }

    public void generateDeploymentId() {
        if (this.deploymentId == null) {
            String dateString = String.valueOf(new Date().getTime());
            this.deploymentId = dateString.substring(dateString.length() - 10);
        }
    }


}
